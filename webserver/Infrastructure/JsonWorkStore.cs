using System.Text.Json;
using WebServer.Application.Models;
using WebServer.Application.Ports;
using WebServer.Domain;

namespace WebServer.Infrastructure;

public sealed class JsonWorkStore : IWorkStorePort
{
    private readonly string metadataPath;
    private readonly JsonSerializerOptions jsonOptions;
    private readonly SemaphoreSlim gate = new(1, 1);
    private List<Segment> segments = new();

    public JsonWorkStore(string metadataPath)
    {
        this.metadataPath = metadataPath;
        jsonOptions = new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
            WriteIndented = true
        };
        Directory.CreateDirectory(Path.GetDirectoryName(metadataPath) ?? ".");
    }

    public async Task InitializeAsync(CancellationToken ct)
    {
        await gate.WaitAsync(ct);
        try
        {
            if (!File.Exists(metadataPath))
            {
                segments = new List<Segment>();
                return;
            }

            await using var stream = File.OpenRead(metadataPath);
            var loaded = await JsonSerializer.DeserializeAsync<List<Segment>>(stream, jsonOptions, ct);
            segments = loaded ?? new List<Segment>();
        }
        finally
        {
            gate.Release();
        }
    }

    public async Task<Segment?> FindSegmentAsync(Guid segmentId, CancellationToken ct)
    {
        await gate.WaitAsync(ct);
        try
        {
            return segments.FirstOrDefault(s => s.SegmentId == segmentId);
        }
        finally
        {
            gate.Release();
        }
    }

    public async Task<Segment?> FindSegmentByUuidAsync(Guid segmentUuid, CancellationToken ct)
    {
        await gate.WaitAsync(ct);
        try
        {
            return segments.FirstOrDefault(s => s.SegmentUuid == segmentUuid);
        }
        finally
        {
            gate.Release();
        }
    }

    public async Task<IReadOnlyList<Segment>> GetSegmentsForWorkAsync(string workId, CancellationToken ct)
    {
        await gate.WaitAsync(ct);
        try
        {
            return segments.Where(s => string.Equals(s.WorkId, workId, StringComparison.OrdinalIgnoreCase)).ToArray();
        }
        finally
        {
            gate.Release();
        }
    }

    public async Task UpsertSegmentAsync(Segment segment, CancellationToken ct)
    {
        await gate.WaitAsync(ct);
        try
        {
            var index = segments.FindIndex(s => s.SegmentUuid == segment.SegmentUuid);
            if (index >= 0)
            {
                segments[index] = segment;
            }
            else
            {
                segments.Add(segment);
            }

            await PersistAsync(ct);
        }
        finally
        {
            gate.Release();
        }
    }

    public async Task<IReadOnlyList<WorkSummaryProjection>> SearchWorksAsync(WorkSearchQuery query, CancellationToken ct)
    {
        await gate.WaitAsync(ct);
        try
        {
            IEnumerable<Segment> filtered = segments;

            if (!string.IsNullOrWhiteSpace(query.WorkId))
            {
                filtered = filtered.Where(s => string.Equals(s.WorkId, query.WorkId, StringComparison.OrdinalIgnoreCase));
            }
            if (!string.IsNullOrWhiteSpace(query.Model))
            {
                filtered = filtered.Where(s => s.Model.Contains(query.Model, StringComparison.OrdinalIgnoreCase));
            }
            if (!string.IsNullOrWhiteSpace(query.Serial))
            {
                filtered = filtered.Where(s => s.Serial.Contains(query.Serial, StringComparison.OrdinalIgnoreCase));
            }
            if (!string.IsNullOrWhiteSpace(query.Process))
            {
                filtered = filtered.Where(s => s.Process.Contains(query.Process, StringComparison.OrdinalIgnoreCase));
            }
            if (query.From is not null)
            {
                filtered = filtered.Where(s => s.RecordedAt >= query.From);
            }
            if (query.To is not null)
            {
                filtered = filtered.Where(s => s.RecordedAt <= query.To);
            }

            return filtered
                .GroupBy(s => s.WorkId, StringComparer.OrdinalIgnoreCase)
                .Select(g =>
                {
                    var ordered = g.OrderBy(s => s.RecordedAt).ThenBy(s => s.SegmentIndex).ToArray();
                    var first = ordered.First();
                    var last = ordered.Last();
                    return new WorkSummaryProjection(first.WorkId, first.Model, first.Serial, first.Process, first.RecordedAt, last.RecordedAt, ordered.Length);
                })
                .ToArray();
        }
        finally
        {
            gate.Release();
        }
    }

    public async Task<IReadOnlyList<string>> GetProcessesAsync(CancellationToken ct)
    {
        await gate.WaitAsync(ct);
        try
        {
            return segments.Select(s => s.Process).Distinct(StringComparer.OrdinalIgnoreCase).ToArray();
        }
        finally
        {
            gate.Release();
        }
    }

    public async Task<IReadOnlyList<Segment>> GetSegmentsOlderThanAsync(DateTimeOffset threshold, CancellationToken ct)
    {
        await gate.WaitAsync(ct);
        try
        {
            return segments.Where(s => s.ReceivedAt <= threshold).ToArray();
        }
        finally
        {
            gate.Release();
        }
    }

    public async Task RemoveSegmentsAsync(IEnumerable<Guid> segmentIds, CancellationToken ct)
    {
        await gate.WaitAsync(ct);
        try
        {
            var idSet = segmentIds.ToHashSet();
            segments = segments.Where(s => !idSet.Contains(s.SegmentId)).ToList();
            await PersistAsync(ct);
        }
        finally
        {
            gate.Release();
        }
    }

    private async Task PersistAsync(CancellationToken ct)
    {
        await using var stream = File.Open(metadataPath, FileMode.Create, FileAccess.Write, FileShare.None);
        await JsonSerializer.SerializeAsync(stream, segments, jsonOptions, ct);
    }
}
