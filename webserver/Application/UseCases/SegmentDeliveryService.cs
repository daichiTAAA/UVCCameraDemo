using WebServer.Application.Models;
using WebServer.Application.Ports;

namespace WebServer.Application.UseCases;

public sealed class SegmentDeliveryService(IWorkStorePort store, IVideoStoragePort storage) : ISegmentDeliveryPort
{
    public async Task<SegmentStreamResult?> GetSegmentAsync(Guid segmentId, bool asAttachment, CancellationToken ct)
    {
        var segment = await store.FindSegmentAsync(segmentId, ct);
        if (segment is null) return null;

        var handle = await storage.TryOpenAsync(segment.LocalPath, ct);
        if (handle is null) return null;

        var view = new SegmentView(segment.SegmentId, segment.SegmentUuid, segment.WorkId, segment.Model, segment.Serial, segment.Process, segment.SegmentIndex, segment.RecordedAt, segment.ReceivedAt, segment.DurationSec, segment.SizeBytes, segment.LocalPath, segment.AdlsPath, segment.ArchivedAt);
        return new SegmentStreamResult(view, handle.Stream, handle.Length, handle.ContentType, handle.LastModified, asAttachment);
    }
}
