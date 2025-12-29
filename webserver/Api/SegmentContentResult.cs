using Microsoft.Net.Http.Headers;
using WebServer.Application.Ports;

namespace WebServer.Api;

public sealed class SegmentContentResult : IResult
{
    private readonly Guid segmentId;
    private readonly bool asAttachment;
    private readonly ISegmentDeliveryPort delivery;

    public SegmentContentResult(Guid segmentId, bool asAttachment, ISegmentDeliveryPort delivery)
    {
        this.segmentId = segmentId;
        this.asAttachment = asAttachment;
        this.delivery = delivery;
    }

    public async Task ExecuteAsync(HttpContext httpContext)
    {
        var ct = httpContext.RequestAborted;

        var rangeHeader = httpContext.Request.Headers.Range.ToString();
        (long? Offset, long? Length, bool HasRange) range = (null, null, false);
        if (!string.IsNullOrWhiteSpace(rangeHeader))
        {
            range = TryParseSingleRange(rangeHeader);
        }

        var result = await delivery.GetSegmentAsync(segmentId, asAttachment,
            range.HasRange ? range.Offset : null,
            range.HasRange ? range.Length : null,
            ct);

        if (result is null)
        {
            httpContext.Response.StatusCode = StatusCodes.Status404NotFound;
            await httpContext.Response.WriteAsJsonAsync(new { error = "NOT_FOUND", message = "segment not found" }, ct);
            return;
        }

        // Validate range against total length.
        if (range.HasRange && (range.Offset is null || range.Offset < 0 || range.Offset >= result.TotalLength))
        {
            httpContext.Response.StatusCode = StatusCodes.Status416RangeNotSatisfiable;
            httpContext.Response.Headers[HeaderNames.ContentRange] = $"bytes */{result.TotalLength}";
            return;
        }

        httpContext.Response.Headers[HeaderNames.AcceptRanges] = "bytes";
        if (result.LastModified is not null)
        {
            httpContext.Response.Headers[HeaderNames.LastModified] = result.LastModified.Value.ToString("R");
        }

        if (asAttachment)
        {
            var fileName = BuildFileName(result.Segment);
            httpContext.Response.Headers[HeaderNames.ContentDisposition] = $"attachment; filename=\"{fileName}\"";
        }

        httpContext.Response.ContentType = result.ContentType;

        if (range.HasRange && range.Offset is not null)
        {
            var start = range.Offset.Value;
            var end = start + result.ContentLength - 1;
            httpContext.Response.StatusCode = StatusCodes.Status206PartialContent;
            httpContext.Response.Headers[HeaderNames.ContentRange] = $"bytes {start}-{end}/{result.TotalLength}";
            httpContext.Response.ContentLength = result.ContentLength;
        }
        else
        {
            httpContext.Response.StatusCode = StatusCodes.Status200OK;
            httpContext.Response.ContentLength = result.ContentLength;
        }

        await using (result.Stream)
        {
            await result.Stream.CopyToAsync(httpContext.Response.Body, 64 * 1024, ct);
        }
    }

    private static (long? Offset, long? Length, bool HasRange) TryParseSingleRange(string header)
    {
        // Supports: bytes=start-end | bytes=start- | bytes=-suffix
        if (!header.StartsWith("bytes=", StringComparison.OrdinalIgnoreCase))
        {
            return (null, null, false);
        }

        var spec = header[6..].Trim();
        // Reject multiple ranges (comma)
        if (spec.Contains(',')) return (null, null, true);

        var dashIndex = spec.IndexOf('-');
        if (dashIndex < 0) return (null, null, true);

        var left = spec[..dashIndex].Trim();
        var right = spec[(dashIndex + 1)..].Trim();

        // bytes=-suffix
        if (left.Length == 0)
        {
            if (!long.TryParse(right, out var suffix) || suffix <= 0)
            {
                return (null, null, true);
            }
            // We cannot resolve suffix without total length here; caller will treat as invalid and return 416.
            return (null, null, true);
        }

        if (!long.TryParse(left, out var start) || start < 0)
        {
            return (null, null, true);
        }

        // bytes=start- (to end)
        if (right.Length == 0)
        {
            return (start, null, true);
        }

        if (!long.TryParse(right, out var end) || end < start)
        {
            return (null, null, true);
        }

        var length = end - start + 1;
        return (start, length, true);
    }

    private static string BuildFileName(WebServer.Application.Models.SegmentView segment)
    {
        var sanitizedWork = string.Concat(segment.WorkId.Select(ch => Path.GetInvalidFileNameChars().Contains(ch) ? '-' : ch));
        return $"{sanitizedWork}-{segment.SegmentIndex}-{segment.SegmentUuid}.mp4";
    }
}
