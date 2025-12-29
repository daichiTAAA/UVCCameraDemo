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

public sealed record WorkSummaryProjection(
    string WorkId,
    string Model,
    string Serial,
    string Process,
    DateTimeOffset FirstRecordedAt,
    DateTimeOffset LastRecordedAt,
    int SegmentCount);

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
