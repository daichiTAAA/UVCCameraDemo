using WebServer.Application.Models;
using WebServer.Domain;

namespace WebServer.Application.Ports;

public interface IWorkStorePort
{
    Task<Segment?> FindSegmentAsync(Guid segmentId, CancellationToken ct);
    Task<Segment?> FindSegmentByUuidAsync(Guid segmentUuid, CancellationToken ct);
    Task<IReadOnlyList<Segment>> GetSegmentsForWorkAsync(string workId, CancellationToken ct);
    Task UpsertSegmentAsync(Segment segment, CancellationToken ct);
    Task<IReadOnlyList<WorkSummaryProjection>> SearchWorksAsync(WorkSearchQuery query, CancellationToken ct);
    Task<IReadOnlyList<string>> GetProcessesAsync(CancellationToken ct);
    Task<IReadOnlyList<Segment>> GetSegmentsOlderThanAsync(DateTimeOffset threshold, CancellationToken ct);
    Task RemoveSegmentsAsync(IEnumerable<Guid> segmentIds, CancellationToken ct);
}

public sealed class WorkSummaryProjection
{
    public string WorkId { get; set; } = string.Empty;
    public string Model { get; set; } = string.Empty;
    public string Serial { get; set; } = string.Empty;
    public string Process { get; set; } = string.Empty;
    public DateTime FirstRecordedAt { get; set; }
    public DateTime LastRecordedAt { get; set; }
    public int SegmentCount { get; set; }
}

public interface IVideoStoragePort
{
    Task<string> PromoteIncomingAsync(string sourcePath, string workId, Guid segmentUuid, CancellationToken ct);
    Task<VideoReadHandle?> TryOpenAsync(string localPath, CancellationToken ct);
    Task<bool> RemoveAsync(string localPath, CancellationToken ct);
    Task<bool> ExistsAsync(string localPath, CancellationToken ct);
}

public sealed record VideoReadHandle(Stream Stream, long Length, string ContentType, DateTimeOffset? LastModified);

public interface IClockPort
{
    DateTimeOffset UtcNow { get; }
}
