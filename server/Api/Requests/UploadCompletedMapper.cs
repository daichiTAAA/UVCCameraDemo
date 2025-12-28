using System.Text;
using WebServer.Application.Models;

namespace WebServer.Api.Requests;

public static class UploadCompletedMapper
{
    public static bool TryMap(UploadCompletedRequest request, out UploadCompletedModel model, out string error)
    {
        error = string.Empty;

        var metadata = request.Tusd?.Upload?.MetaData;
        var segmentUuidText = request.SegmentUuid ?? ReadMeta(metadata, "segmentuuid");
        if (!Guid.TryParse(segmentUuidText, out var segmentUuid))
        {
            error = "segmentUuid is required and must be a UUID";
            model = default!;
            return false;
        }

        var workId = request.WorkId ?? ReadMeta(metadata, "workid");
        var modelName = request.Model ?? ReadMeta(metadata, "model");
        var serial = request.Serial ?? ReadMeta(metadata, "serial");
        var process = request.Process ?? ReadMeta(metadata, "process");

        if (string.IsNullOrWhiteSpace(workId) || string.IsNullOrWhiteSpace(modelName) || string.IsNullOrWhiteSpace(serial) || string.IsNullOrWhiteSpace(process))
        {
            error = "workId, model, serial, process are required";
            model = default!;
            return false;
        }

        var segmentIndexValue = request.SegmentIndex ?? ParseInt(ReadMeta(metadata, "segmentindex"));
        if (segmentIndexValue is null)
        {
            error = "segmentIndex is required";
            model = default!;
            return false;
        }

        var recordedAtText = request.RecordedAt?.ToString("O") ?? ReadMeta(metadata, "recordedat");
        if (!DateTimeOffset.TryParse(recordedAtText, out var recordedAt))
        {
            error = "recordedAt is required and must be ISO 8601";
            model = default!;
            return false;
        }

        var duration = request.DurationSec ?? ParseInt(ReadMeta(metadata, "durationsec"));
        var size = request.SizeBytes ?? request.Tusd?.Upload?.Size;
        var sha256 = request.Sha256 ?? ReadMeta(metadata, "sha256");

        var sourcePath = request.LocalFilePath
            ?? request.Tusd?.Upload?.Storage?.Path
            ?? request.Tusd?.Upload?.Storage?.RelativePath;

        if (string.IsNullOrWhiteSpace(sourcePath))
        {
            error = "localFilePath (or tusd storage path) is required";
            model = default!;
            return false;
        }

        model = new UploadCompletedModel(segmentUuid, workId, modelName, serial, process, segmentIndexValue.Value, recordedAt, duration, size, sourcePath, sha256);
        return true;
    }

    private static int? ParseInt(string? value)
    {
        if (value is null) return null;
        return int.TryParse(value, out var v) ? v : null;
    }

    private static string? ReadMeta(Dictionary<string, string>? metadata, string key)
    {
        if (metadata is null) return null;
        var kvp = metadata.FirstOrDefault(k => string.Equals(k.Key, key, StringComparison.OrdinalIgnoreCase));
        if (string.IsNullOrEmpty(kvp.Key)) return null;

        try
        {
            var raw = Convert.FromBase64String(kvp.Value);
            return Encoding.UTF8.GetString(raw);
        }
        catch
        {
            return null;
        }
    }
}
