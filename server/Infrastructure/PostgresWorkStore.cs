using System.Data;
using Dapper;
using Npgsql;
using WebServer.Application.Models;
using WebServer.Application.Ports;
using WebServer.Domain;

namespace WebServer.Infrastructure;

public sealed class PostgresWorkStore : IWorkStorePort
{
    private readonly string connectionString;
    private readonly ILogger<PostgresWorkStore> logger;

    public PostgresWorkStore(string connectionString, ILogger<PostgresWorkStore> logger)
    {
        this.connectionString = connectionString;
        this.logger = logger;
    }

    public async Task InitializeAsync(CancellationToken ct)
    {
        await using var conn = new NpgsqlConnection(connectionString);
        await conn.OpenAsync(ct);

        const string sql = @"
CREATE TABLE IF NOT EXISTS works (
    work_id TEXT PRIMARY KEY,
    model TEXT NOT NULL,
    serial TEXT NOT NULL,
    process TEXT NOT NULL,
    first_recorded_at TIMESTAMPTZ NOT NULL,
    last_recorded_at TIMESTAMPTZ NOT NULL,
    segment_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS segments (
    segment_id UUID PRIMARY KEY,
    segment_uuid UUID NOT NULL UNIQUE,
    work_id TEXT NOT NULL REFERENCES works(work_id) ON DELETE CASCADE,
    segment_index INTEGER NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    duration_sec INTEGER NULL,
    size_bytes BIGINT NULL,
    local_path TEXT NOT NULL,
    sha256 TEXT NULL,
    adls_path TEXT NULL,
    archived_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_segments_work_recorded ON segments(work_id, recorded_at);
";

        await conn.ExecuteAsync(new CommandDefinition(sql, cancellationToken: ct));
        logger.LogInformation("PostgreSQL schema ensured");
    }

    public async Task<Segment?> FindSegmentAsync(Guid segmentId, CancellationToken ct)
    {
        await using var conn = new NpgsqlConnection(connectionString);
        const string sql = "SELECT * FROM segments WHERE segment_id = @segmentId";
        var row = await conn.QuerySingleOrDefaultAsync<SegmentRow>(new CommandDefinition(sql, new { segmentId }, cancellationToken: ct));
        return row?.ToDomain();
    }

    public async Task<Segment?> FindSegmentByUuidAsync(Guid segmentUuid, CancellationToken ct)
    {
        await using var conn = new NpgsqlConnection(connectionString);
        const string sql = "SELECT * FROM segments WHERE segment_uuid = @segmentUuid";
        var row = await conn.QuerySingleOrDefaultAsync<SegmentRow>(new CommandDefinition(sql, new { segmentUuid }, cancellationToken: ct));
        return row?.ToDomain();
    }

    public async Task<IReadOnlyList<Segment>> GetSegmentsForWorkAsync(string workId, CancellationToken ct)
    {
        await using var conn = new NpgsqlConnection(connectionString);
        const string sql = "SELECT * FROM segments WHERE work_id = @workId ORDER BY recorded_at, segment_index";
        var rows = await conn.QueryAsync<SegmentRow>(new CommandDefinition(sql, new { workId }, cancellationToken: ct));
        return rows.Select(r => r.ToDomain()).ToArray();
    }

    public async Task UpsertSegmentAsync(Segment segment, CancellationToken ct)
    {
        await using var conn = new NpgsqlConnection(connectionString);
        await conn.OpenAsync(ct);
        await using var tx = await conn.BeginTransactionAsync(ct);

        var now = DateTimeOffset.UtcNow;

        const string ensureWorkSql = @"
INSERT INTO works (work_id, model, serial, process, first_recorded_at, last_recorded_at, segment_count, created_at, updated_at)
VALUES (@WorkId, @Model, @Serial, @Process, @RecordedAt, @RecordedAt, 0, @Now, @Now)
ON CONFLICT (work_id) DO NOTHING;";

        await conn.ExecuteAsync(new CommandDefinition(ensureWorkSql, new
        {
            segment.WorkId,
            segment.Model,
            segment.Serial,
            segment.Process,
            segment.RecordedAt,
            Now = now
        }, transaction: tx, cancellationToken: ct));

        string? previousWorkId = await conn.QuerySingleOrDefaultAsync<string?>(
            new CommandDefinition("SELECT work_id FROM segments WHERE segment_uuid = @segmentUuid", new { segment.SegmentUuid }, transaction: tx, cancellationToken: ct));

        const string upsertSql = @"
INSERT INTO segments (
    segment_id, segment_uuid, work_id, segment_index, recorded_at, received_at, duration_sec, size_bytes, local_path, sha256, adls_path, archived_at, created_at, updated_at
) VALUES (
    @SegmentId, @SegmentUuid, @WorkId, @SegmentIndex, @RecordedAt, @ReceivedAt, @DurationSec, @SizeBytes, @LocalPath, @Sha256, @AdlsPath, @ArchivedAt, @CreatedAt, @UpdatedAt
) ON CONFLICT (segment_uuid) DO UPDATE SET
    work_id = EXCLUDED.work_id,
    segment_index = EXCLUDED.segment_index,
    recorded_at = EXCLUDED.recorded_at,
    received_at = EXCLUDED.received_at,
    duration_sec = EXCLUDED.duration_sec,
    size_bytes = EXCLUDED.size_bytes,
    local_path = EXCLUDED.local_path,
    sha256 = EXCLUDED.sha256,
    adls_path = EXCLUDED.adls_path,
    archived_at = EXCLUDED.archived_at,
    updated_at = EXCLUDED.updated_at
RETURNING segment_id, created_at;";

        var returned = await conn.QuerySingleAsync<(Guid segment_id, DateTimeOffset created_at)>(
            new CommandDefinition(upsertSql, segment, transaction: tx, cancellationToken: ct));

        // Ensure works table reflects latest aggregates
        await UpdateWorkAggregatesAsync(conn, tx, segment.WorkId, segment, ct);

        if (previousWorkId is not null && !string.Equals(previousWorkId, segment.WorkId, StringComparison.Ordinal))
        {
            await UpdateWorkAggregatesAsync(conn, tx, previousWorkId, segment, ct);
        }

        await tx.CommitAsync(ct);

        // Keep IDs/createdAt stable in memory
        segment.SegmentId = returned.segment_id;
        segment.CreatedAt = returned.created_at;
    }

    public async Task<IReadOnlyList<WorkSummaryProjection>> SearchWorksAsync(WorkSearchQuery query, CancellationToken ct)
    {
        await using var conn = new NpgsqlConnection(connectionString);
        var conditions = new List<string>();
        var parameters = new DynamicParameters();

        if (!string.IsNullOrWhiteSpace(query.WorkId)) { conditions.Add("work_id = @workId"); parameters.Add("workId", query.WorkId); }
        if (!string.IsNullOrWhiteSpace(query.Model)) { conditions.Add("model ILIKE @model"); parameters.Add("model", $"%{query.Model}%"); }
        if (!string.IsNullOrWhiteSpace(query.Serial)) { conditions.Add("serial ILIKE @serial"); parameters.Add("serial", $"%{query.Serial}%"); }
        if (!string.IsNullOrWhiteSpace(query.Process)) { conditions.Add("process ILIKE @process"); parameters.Add("process", $"%{query.Process}%"); }
        if (query.From is not null) { conditions.Add("last_recorded_at >= @from"); parameters.Add("from", query.From); }
        if (query.To is not null) { conditions.Add("first_recorded_at <= @to"); parameters.Add("to", query.To); }

        var where = conditions.Count > 0 ? $"WHERE {string.Join(" AND ", conditions)}" : string.Empty;
        var sql = $"SELECT work_id AS WorkId, model AS Model, serial AS Serial, process AS Process, first_recorded_at AS FirstRecordedAt, last_recorded_at AS LastRecordedAt, segment_count AS SegmentCount FROM works {where}";

        var rows = await conn.QueryAsync<WorkSummaryProjection>(new CommandDefinition(sql, parameters, cancellationToken: ct));
        return rows.ToArray();
    }

    public async Task<IReadOnlyList<string>> GetProcessesAsync(CancellationToken ct)
    {
        await using var conn = new NpgsqlConnection(connectionString);
        const string sql = "SELECT DISTINCT process FROM works";
        var rows = await conn.QueryAsync<string>(new CommandDefinition(sql, cancellationToken: ct));
        return rows.ToArray();
    }

    public async Task<IReadOnlyList<Segment>> GetSegmentsOlderThanAsync(DateTimeOffset threshold, CancellationToken ct)
    {
        await using var conn = new NpgsqlConnection(connectionString);
        const string sql = "SELECT * FROM segments WHERE received_at <= @threshold";
        var rows = await conn.QueryAsync<SegmentRow>(new CommandDefinition(sql, new { threshold }, cancellationToken: ct));
        return rows.Select(r => r.ToDomain()).ToArray();
    }

    public async Task RemoveSegmentsAsync(IEnumerable<Guid> segmentIds, CancellationToken ct)
    {
        var ids = segmentIds.ToArray();
        if (ids.Length == 0) return;

        await using var conn = new NpgsqlConnection(connectionString);
        await conn.OpenAsync(ct);
        await using var tx = await conn.BeginTransactionAsync(ct);

        var workIds = await conn.QueryAsync<string>(new CommandDefinition(
            "SELECT DISTINCT work_id FROM segments WHERE segment_id = ANY(@ids)", new { ids }, transaction: tx, cancellationToken: ct));

        await conn.ExecuteAsync(new CommandDefinition("DELETE FROM segments WHERE segment_id = ANY(@ids)", new { ids }, transaction: tx, cancellationToken: ct));

        foreach (var workId in workIds)
        {
            await UpdateWorkAggregatesAsync(conn, tx, workId, null, ct);
        }

        await tx.CommitAsync(ct);
    }

    private async Task UpdateWorkAggregatesAsync(NpgsqlConnection conn, NpgsqlTransaction tx, string workId, Segment? latestSegment, CancellationToken ct)
    {
        const string aggSql = @"
SELECT 
    MIN(recorded_at) AS FirstRecordedAt,
    MAX(recorded_at) AS LastRecordedAt,
    COUNT(*) AS SegmentCount
FROM segments
WHERE work_id = @workId";

        var agg = await conn.QuerySingleOrDefaultAsync<(DateTimeOffset? FirstRecordedAt, DateTimeOffset? LastRecordedAt, int SegmentCount)>(
            new CommandDefinition(aggSql, new { workId }, transaction: tx, cancellationToken: ct));

        if (agg.SegmentCount == 0 || agg.FirstRecordedAt is null || agg.LastRecordedAt is null)
        {
            await conn.ExecuteAsync(new CommandDefinition("DELETE FROM works WHERE work_id = @workId", new { workId }, transaction: tx, cancellationToken: ct));
            return;
        }

        var now = DateTimeOffset.UtcNow;
        const string upsertWork = @"
INSERT INTO works (work_id, model, serial, process, first_recorded_at, last_recorded_at, segment_count, created_at, updated_at)
VALUES (@WorkId, @Model, @Serial, @Process, @FirstRecordedAt, @LastRecordedAt, @SegmentCount, @Now, @Now)
ON CONFLICT (work_id) DO UPDATE SET
    model = EXCLUDED.model,
    serial = EXCLUDED.serial,
    process = EXCLUDED.process,
    first_recorded_at = EXCLUDED.first_recorded_at,
    last_recorded_at = EXCLUDED.last_recorded_at,
    segment_count = EXCLUDED.segment_count,
    updated_at = EXCLUDED.updated_at;";

        var model = latestSegment?.Model ?? await conn.QuerySingleAsync<string>(new CommandDefinition("SELECT model FROM works WHERE work_id = @workId LIMIT 1", new { workId }, transaction: tx, cancellationToken: ct));
        var serial = latestSegment?.Serial ?? await conn.QuerySingleAsync<string>(new CommandDefinition("SELECT serial FROM works WHERE work_id = @workId LIMIT 1", new { workId }, transaction: tx, cancellationToken: ct));
        var process = latestSegment?.Process ?? await conn.QuerySingleAsync<string>(new CommandDefinition("SELECT process FROM works WHERE work_id = @workId LIMIT 1", new { workId }, transaction: tx, cancellationToken: ct));

        await conn.ExecuteAsync(new CommandDefinition(upsertWork, new
        {
            WorkId = workId,
            Model = model,
            Serial = serial,
            Process = process,
            FirstRecordedAt = agg.FirstRecordedAt,
            LastRecordedAt = agg.LastRecordedAt,
            SegmentCount = agg.SegmentCount,
            Now = now
        }, transaction: tx, cancellationToken: ct));
    }

    private sealed class SegmentRow
    {
        public Guid SegmentId { get; set; }
        public Guid SegmentUuid { get; set; }
        public string WorkId { get; set; } = string.Empty;
        public string Model { get; set; } = string.Empty;
        public string Serial { get; set; } = string.Empty;
        public string Process { get; set; } = string.Empty;
        public int SegmentIndex { get; set; }
        public DateTimeOffset RecordedAt { get; set; }
        public DateTimeOffset ReceivedAt { get; set; }
        public int? DurationSec { get; set; }
        public long? SizeBytes { get; set; }
        public string LocalPath { get; set; } = string.Empty;
        public string? Sha256 { get; set; }
        public string? AdlsPath { get; set; }
        public DateTimeOffset? ArchivedAt { get; set; }
        public DateTimeOffset CreatedAt { get; set; }
        public DateTimeOffset UpdatedAt { get; set; }

        public Segment ToDomain() => new()
        {
            SegmentId = SegmentId,
            SegmentUuid = SegmentUuid,
            WorkId = WorkId,
            Model = Model,
            Serial = Serial,
            Process = Process,
            SegmentIndex = SegmentIndex,
            RecordedAt = RecordedAt,
            ReceivedAt = ReceivedAt,
            DurationSec = DurationSec,
            SizeBytes = SizeBytes,
            LocalPath = LocalPath,
            Sha256 = Sha256,
            AdlsPath = AdlsPath,
            ArchivedAt = ArchivedAt,
            CreatedAt = CreatedAt,
            UpdatedAt = UpdatedAt
        };
    }
}
