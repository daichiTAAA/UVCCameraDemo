using WebServer.Application.Models;

namespace WebServer.Application.Ports;

public interface IMetadataQueryPort
{
    Task<IReadOnlyList<ProcessInfo>> GetProcessesAsync(CancellationToken ct);
    Task<IReadOnlyList<WorkSummary>> SearchWorksAsync(WorkSearchQuery query, CancellationToken ct);
    Task<WorkDetail?> GetWorkDetailAsync(string workId, CancellationToken ct);
}

public interface ISegmentDeliveryPort
{
    Task<SegmentStreamResult?> GetSegmentAsync(Guid segmentId, bool asAttachment, long? offset, long? length, CancellationToken ct);
}

public interface IIngestionAndLifecyclePort
{
    Task<IngestionResult> HandleUploadCompletedAsync(UploadCompletedModel model, CancellationToken ct);
    Task<int> RunCleanupAsync(TimeSpan retention, bool allowDeleteUnarchived, CancellationToken ct);
    Task<int> RunArchiveAsync(CancellationToken ct);
}

public sealed record SegmentStreamResult(
    SegmentView Segment,
    Stream Stream,
    long TotalLength,
    long ContentLength,
    string ContentType,
    DateTimeOffset? LastModified,
    bool IsAttachment);
