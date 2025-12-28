namespace WebServer.Application.Models;

public sealed record ProcessInfo(string Name);

public sealed record WorkSearchQuery(
    string? WorkId,
    string? Model,
    string? Serial,
    string? Process,
    DateTimeOffset? From,
    DateTimeOffset? To);

public sealed record WorkSummary(
    string WorkId,
    string Model,
    string Serial,
    string Process,
    DateTimeOffset FirstRecordedAt,
    DateTimeOffset LastRecordedAt,
    int SegmentCount);

public sealed record SegmentView(
    Guid SegmentId,
    Guid SegmentUuid,
    string WorkId,
    string Model,
    string Serial,
    string Process,
    int SegmentIndex,
    DateTimeOffset RecordedAt,
    DateTimeOffset ReceivedAt,
    int? DurationSec,
    long? SizeBytes,
    string LocalPath,
    string? AdlsPath,
    DateTimeOffset? ArchivedAt);

public sealed record WorkDetail(
    string WorkId,
    string Model,
    string Serial,
    string Process,
    DateTimeOffset FirstRecordedAt,
    DateTimeOffset LastRecordedAt,
    IReadOnlyList<SegmentView> Segments);
