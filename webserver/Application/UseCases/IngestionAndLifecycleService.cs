using WebServer.Application.Models;
using WebServer.Application.Ports;
using WebServer.Configuration;
using WebServer.Domain;
using Microsoft.Extensions.Options;

namespace WebServer.Application.UseCases;

public sealed class IngestionAndLifecycleService(
    IMetadataStorePort store,
    IVideoStoragePort storage,
    IArchiveStoragePort archive,
    IOptions<AdlsOptions> adlsOptions,
    IClockPort clock,
    ILogger<IngestionAndLifecycleService> logger) : IIngestionAndLifecyclePort
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
        var deletedCount = 0;

        foreach (var segment in candidates)
        {
            if (!allowDeleteUnarchived && segment.AdlsPath is null)
            {
                logger.LogInformation("Skip deleting segment {SegmentId} because it is not archived", segment.SegmentId);
                continue;
            }

            if (await storage.RemoveAsync(segment.LocalPath, ct))
            {
                deletedCount++;
                logger.LogInformation("Deleted local segment {SegmentId} at {Path}", segment.SegmentId, segment.LocalPath);
            }
            else
            {
                logger.LogWarning("Failed to delete local segment {SegmentId} at {Path}", segment.SegmentId, segment.LocalPath);
            }
        }

        // Keep DB metadata for search/detail after local deletion.
        return deletedCount;
    }

    public async Task<int> RunArchiveAsync(CancellationToken ct)
    {
        if (!adlsOptions.Value.Enabled)
        {
            logger.LogInformation("Archive job skipped (ADLS disabled)");
            return 0;
        }

        var options = adlsOptions.Value;
        var prefix = string.IsNullOrWhiteSpace(options.Prefix) ? "videos" : options.Prefix.Trim('/');

        var segments = await store.GetUnarchivedSegmentsAsync(limit: 200, ct);
        var archived = 0;

        foreach (var segment in segments)
        {
            if (!await storage.ExistsAsync(segment.LocalPath, ct))
            {
                logger.LogInformation("Skip archiving segment {SegmentId}: local missing", segment.SegmentId);
                continue;
            }

            // Use recordedAt as organization key (spec).
            var date = segment.RecordedAt.UtcDateTime;
            var adlsPath = $"{prefix}/{date:yyyy/MM/dd}/{segment.WorkId}/{segment.SegmentUuid}.mp4";

            var ok = await archive.UploadAsync(segment.LocalPath, adlsPath, ct);
            if (!ok)
            {
                logger.LogWarning("Archive failed for segment {SegmentId}", segment.SegmentId);
                continue;
            }

            var now = clock.UtcNow;
            await store.MarkSegmentArchivedAsync(segment.SegmentId, adlsPath, now, ct);
            archived++;
        }

        logger.LogInformation("Archive job completed: archived={Archived}", archived);
        return archived;
    }
}
