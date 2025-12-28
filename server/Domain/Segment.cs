namespace WebServer.Domain;

public sealed class Segment
{
    public Guid SegmentId { get; init; }
    public Guid SegmentUuid { get; init; }
    public string WorkId { get; init; } = string.Empty;
    public string Model { get; init; } = string.Empty;
    public string Serial { get; init; } = string.Empty;
    public string Process { get; init; } = string.Empty;
    public int SegmentIndex { get; init; }
    public DateTimeOffset RecordedAt { get; init; }
    public DateTimeOffset ReceivedAt { get; init; }
    public int? DurationSec { get; init; }
    public long? SizeBytes { get; init; }
    public string LocalPath { get; set; } = string.Empty;
    public string? Sha256 { get; init; }
    public string? AdlsPath { get; init; }
    public DateTimeOffset? ArchivedAt { get; init; }
    public DateTimeOffset CreatedAt { get; init; }
    public DateTimeOffset UpdatedAt { get; set; }
}
