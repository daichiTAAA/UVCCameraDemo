using WebServer.Application.Models;
using WebServer.Application.Ports;

namespace WebServer.Application.UseCases;

public sealed class SegmentDeliveryService(IMetadataStorePort store, IVideoStoragePort storage, IArchiveStoragePort archive) : ISegmentDeliveryPort
{
    public async Task<SegmentStreamResult?> GetSegmentAsync(Guid segmentId, bool asAttachment, long? offset, long? length, CancellationToken ct)
    {
        var segment = await store.FindSegmentAsync(segmentId, ct);
        if (segment is null) return null;

        // Prefer local. If missing, fallback to ADLS when available.
        var view = new SegmentView(segment.SegmentId, segment.SegmentUuid, segment.WorkId, segment.Model, segment.Serial, segment.Process, segment.SegmentIndex, segment.RecordedAt, segment.ReceivedAt, segment.DurationSec, segment.SizeBytes, segment.LocalPath, segment.AdlsPath, segment.ArchivedAt);

        var localHandle = await storage.TryOpenAsync(segment.LocalPath, ct);
        if (localHandle is not null)
        {
            return CreateLocalResult(view, localHandle, asAttachment, offset, length);
        }

        if (string.IsNullOrWhiteSpace(segment.AdlsPath)) return null;

        var props = await archive.TryGetPropertiesAsync(segment.AdlsPath, ct);
        if (props is null) return null;

        var totalLength = props.Length;
        var (resolvedOffset, resolvedLength) = ResolveRange(totalLength, offset, length);
        Stream? stream;
        if (resolvedOffset is null)
        {
            stream = await archive.TryOpenReadAsync(segment.AdlsPath, ct);
            if (stream is null) return null;
            var contentType = props.ContentType ?? "video/mp4";
            return new SegmentStreamResult(view, stream, totalLength, totalLength, contentType, props.LastModified, asAttachment);
        }

        var readLength = resolvedLength ?? (totalLength - resolvedOffset.Value);
        stream = await archive.TryOpenReadRangeAsync(segment.AdlsPath, resolvedOffset.Value, readLength, ct);
        if (stream is null) return null;

        {
            var contentType = props.ContentType ?? "video/mp4";
            return new SegmentStreamResult(view, stream, totalLength, readLength, contentType, props.LastModified, asAttachment);
        }
    }

    private static SegmentStreamResult CreateLocalResult(SegmentView view, VideoReadHandle handle, bool asAttachment, long? offset, long? length)
    {
        var totalLength = handle.Length;
        var (resolvedOffset, resolvedLength) = ResolveRange(totalLength, offset, length);

        if (resolvedOffset is null)
        {
            return new SegmentStreamResult(view, handle.Stream, totalLength, totalLength, handle.ContentType, handle.LastModified, asAttachment);
        }

        if (handle.Stream.CanSeek)
        {
            handle.Stream.Seek(resolvedOffset.Value, SeekOrigin.Begin);
            var readLength = resolvedLength ?? (totalLength - resolvedOffset.Value);
            var limited = new LimitedStream(handle.Stream, readLength);
            return new SegmentStreamResult(view, limited, totalLength, readLength, handle.ContentType, handle.LastModified, asAttachment);
        }

        // Fallback: if we can't seek, return full stream.
        return new SegmentStreamResult(view, handle.Stream, totalLength, totalLength, handle.ContentType, handle.LastModified, asAttachment);
    }

    private static (long? Offset, long? Length) ResolveRange(long totalLength, long? offset, long? length)
    {
        if (offset is null) return (null, null);
        if (offset < 0) return (null, null);
        if (offset >= totalLength) return (null, null);

        if (length is null)
        {
            // bytes=<start>- (to end)
            return (offset.Value, null);
        }

        if (length <= 0) return (null, null);

        var maxLen = totalLength - offset.Value;
        var resolvedLength = Math.Min(length.Value, maxLen);
        return (offset.Value, resolvedLength);
    }

    private sealed class LimitedStream : Stream
    {
        private readonly Stream inner;
        private long remaining;

        public LimitedStream(Stream inner, long length)
        {
            this.inner = inner;
            remaining = length;
        }

        public override bool CanRead => inner.CanRead;
        public override bool CanSeek => false;
        public override bool CanWrite => false;
        public override long Length => remaining;
        public override long Position
        {
            get => 0;
            set => throw new NotSupportedException();
        }

        public override void Flush() => throw new NotSupportedException();

        public override int Read(byte[] buffer, int offset, int count)
        {
            if (remaining <= 0) return 0;
            var toRead = (int)Math.Min(count, remaining);
            var read = inner.Read(buffer, offset, toRead);
            remaining -= read;
            return read;
        }

        public override async ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default)
        {
            if (remaining <= 0) return 0;
            var toRead = (int)Math.Min(buffer.Length, remaining);
            var read = await inner.ReadAsync(buffer[..toRead], cancellationToken);
            remaining -= read;
            return read;
        }

        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    }
}
