using System.Text.Json.Serialization;

namespace WebServer.Api.Requests;

public sealed class UploadCompletedRequest
{
    // Direct payload (optional when tusd metadata is decoded automatically)
    public string? SegmentUuid { get; init; }
    public string? WorkId { get; init; }
    public string? Model { get; init; }
    public string? Serial { get; init; }
    public string? Process { get; init; }
    public int? SegmentIndex { get; init; }
    public DateTimeOffset? RecordedAt { get; init; }
    public int? DurationSec { get; init; }
    public long? SizeBytes { get; init; }
    public string? LocalFilePath { get; init; }
    public string? Sha256 { get; init; }

    // tusd hook payload (optional)
    public TusdHookRequest? Tusd { get; init; }
}

public sealed class TusdHookRequest
{
    public string? Type { get; init; }
    public TusdHookUpload? Upload { get; init; }
}

public sealed class TusdHookUpload
{
    public string? ID { get; init; }
    public TusdHookStorage? Storage { get; init; }
    public Dictionary<string, string>? MetaData { get; init; }
    public long? Size { get; init; }
}

public sealed class TusdHookStorage
{
    public string? Path { get; init; }
    [JsonPropertyName("RelativePath")]
    public string? RelativePath { get; init; }
}
