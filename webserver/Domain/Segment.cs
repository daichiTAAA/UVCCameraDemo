namespace WebServer.Domain;

public sealed class Segment
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
}
