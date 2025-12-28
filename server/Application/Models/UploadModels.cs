namespace WebServer.Application.Models;

public sealed record UploadCompletedModel(
    Guid SegmentUuid,
    string WorkId,
    string Model,
    string Serial,
    string Process,
    int SegmentIndex,
    DateTimeOffset RecordedAt,
    int? DurationSec,
    long? SizeBytes,
    string SourcePath,
    string? Sha256);

public sealed record IngestionResult(
    Guid SegmentId,
    Guid SegmentUuid,
    string WorkId,
    string LocalPath,
    bool WasUpdated);
