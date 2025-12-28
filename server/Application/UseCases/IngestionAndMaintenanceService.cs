using WebServer.Application.Models;
using WebServer.Application.Ports;
using WebServer.Domain;

namespace WebServer.Application.UseCases;

public sealed class IngestionAndMaintenanceService(
    IWorkStorePort store,
    IVideoStoragePort storage,
    IClockPort clock,
    ILogger<IngestionAndMaintenanceService> logger) : IIngestionAndMaintenancePort
{
    public async Task<IngestionResult> HandleUploadCompletedAsync(UploadCompletedModel model, CancellationToken ct)
    {
        var now = clock.UtcNow;
        var existing = await store.FindSegmentByUuidAsync(model.SegmentUuid, ct);

        var segmentId = existing?.SegmentId ?? Guid.NewGuid();
        var createdAt = existing?.CreatedAt ?? now;
        var receivedAt = existing?.ReceivedAt ?? now;

        var targetPath = await storage.PromoteIncomingAsync(model.SourcePath, model.WorkId, model.SegmentUuid, ct);

        var segment = new Segment
        {
            SegmentId = segmentId,
            SegmentUuid = model.SegmentUuid,
            WorkId = model.WorkId,
            Model = model.Model,
            Serial = model.Serial,
            Process = model.Process,
            SegmentIndex = model.SegmentIndex,
            RecordedAt = model.RecordedAt,
            ReceivedAt = receivedAt,
            DurationSec = model.DurationSec,
            SizeBytes = model.SizeBytes,
            LocalPath = targetPath,
            Sha256 = model.Sha256,
            AdlsPath = existing?.AdlsPath,
            ArchivedAt = existing?.ArchivedAt,
            CreatedAt = createdAt,
            UpdatedAt = now
        };

        await store.UpsertSegmentAsync(segment, ct);
        var wasUpdated = existing is not null;

        logger.LogInformation("Handled upload completed for work {WorkId} segmentUuid {SegmentUuid} (updated={Updated})", model.WorkId, model.SegmentUuid, wasUpdated);
        return new IngestionResult(segment.SegmentId, segment.SegmentUuid, segment.WorkId, segment.LocalPath, wasUpdated);
    }

    public async Task<int> RunCleanupAsync(TimeSpan retention, bool allowDeleteUnarchived, CancellationToken ct)
    {
        var threshold = clock.UtcNow - retention;
        var candidates = await store.GetSegmentsOlderThanAsync(threshold, ct);
        var removedIds = new List<Guid>();

        foreach (var segment in candidates)
        {
            if (!allowDeleteUnarchived && segment.AdlsPath is null)
            {
                logger.LogInformation("Skip deleting segment {SegmentId} because it is not archived", segment.SegmentId);
                continue;
            }

            if (await storage.RemoveAsync(segment.LocalPath, ct))
            {
                removedIds.Add(segment.SegmentId);
                logger.LogInformation("Deleted local segment {SegmentId} at {Path}", segment.SegmentId, segment.LocalPath);
            }
            else
            {
                logger.LogWarning("Failed to delete local segment {SegmentId} at {Path}", segment.SegmentId, segment.LocalPath);
            }
        }

        if (removedIds.Count > 0)
        {
            await store.RemoveSegmentsAsync(removedIds, ct);
        }

        return removedIds.Count;
    }

    public Task<int> RunArchiveAsync(CancellationToken ct)
    {
        // ADLSアーカイブは未実装（スタブ）。必要になったらここでSDK連携を追加する。
        logger.LogInformation("Archive job stub executed (no-op)");
        return Task.FromResult(0);
    }
}
