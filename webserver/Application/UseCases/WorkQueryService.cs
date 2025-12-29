using WebServer.Application.Models;
using WebServer.Application.Ports;

namespace WebServer.Application.UseCases;

public sealed class WorkQueryService(IWorkStorePort store) : IWorkQueryPort
{
    public async Task<IReadOnlyList<ProcessInfo>> GetProcessesAsync(CancellationToken ct)
    {
        var processes = await store.GetProcessesAsync(ct);
        return processes.Distinct(StringComparer.OrdinalIgnoreCase)
            .Where(p => !string.IsNullOrWhiteSpace(p))
            .OrderBy(p => p, StringComparer.OrdinalIgnoreCase)
            .Select(p => new ProcessInfo(p))
            .ToArray();
    }

    public async Task<IReadOnlyList<WorkSummary>> SearchWorksAsync(WorkSearchQuery query, CancellationToken ct)
    {
        var projections = await store.SearchWorksAsync(query, ct);
        return projections
            .OrderByDescending(p => p.LastRecordedAt)
            .Select(p => new WorkSummary(
                p.WorkId,
                p.Model,
                p.Serial,
                p.Process,
                ToUtcOffset(p.FirstRecordedAt),
                ToUtcOffset(p.LastRecordedAt),
                p.SegmentCount))
            .ToArray();
    }

    public async Task<WorkDetail?> GetWorkDetailAsync(string workId, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(workId)) return null;

        var segments = await store.GetSegmentsForWorkAsync(workId, ct);
        if (segments.Count == 0) return null;

        var ordered = segments.OrderBy(s => s.RecordedAt).ThenBy(s => s.SegmentIndex).ToArray();
        var first = ordered.First();
        var last = ordered.Last();
        var segmentViews = ordered
            .Select(s => new SegmentView(s.SegmentId, s.SegmentUuid, s.WorkId, s.Model, s.Serial, s.Process, s.SegmentIndex, s.RecordedAt, s.ReceivedAt, s.DurationSec, s.SizeBytes, s.LocalPath, s.AdlsPath, s.ArchivedAt))
            .ToArray();

        return new WorkDetail(first.WorkId, first.Model, first.Serial, first.Process, first.RecordedAt, last.RecordedAt, segmentViews);
    }

    private static DateTimeOffset ToUtcOffset(DateTime value)
    {
        return value.Kind switch
        {
            DateTimeKind.Utc => new DateTimeOffset(value, TimeSpan.Zero),
            DateTimeKind.Local => value.ToUniversalTime(),
            _ => new DateTimeOffset(DateTime.SpecifyKind(value, DateTimeKind.Utc), TimeSpan.Zero)
        };
    }
}
