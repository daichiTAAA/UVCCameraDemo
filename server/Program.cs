using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using WebServer.Api.Requests;
using WebServer.Application.Models;
using WebServer.Application.Ports;
using WebServer.Application.UseCases;
using WebServer.Configuration;
using WebServer.Infrastructure;

var builder = WebApplication.CreateBuilder(args);

builder.Services.Configure<StorageOptions>(builder.Configuration.GetSection("Storage"));
builder.Services.Configure<SecurityOptions>(builder.Configuration.GetSection("Security"));
builder.Services.Configure<MaintenanceOptions>(builder.Configuration.GetSection("Maintenance"));

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

builder.Services.AddSingleton<IClockPort, SystemClock>();
builder.Services.AddSingleton<IWorkStorePort>(sp =>
{
    var options = sp.GetRequiredService<IOptions<StorageOptions>>();
    var store = new JsonWorkStore(options.Value.MetadataPath);
    store.InitializeAsync(CancellationToken.None).GetAwaiter().GetResult();
    return store;
});
builder.Services.AddSingleton<IVideoStoragePort>(sp => new FileSystemVideoStorage(sp.GetRequiredService<IOptions<StorageOptions>>().Value));
builder.Services.AddSingleton<IWorkQueryPort, WorkQueryService>();
builder.Services.AddSingleton<ISegmentDeliveryPort, SegmentDeliveryService>();
builder.Services.AddSingleton<IIngestionAndMaintenancePort, IngestionAndMaintenanceService>();

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseMiddleware<ApiKeyMiddleware>();

app.MapGet("/api/health", () => Results.Ok(new { status = "ok" }));

app.MapGet("/api/processes", async (IWorkQueryPort port, CancellationToken ct) =>
{
    var result = await port.GetProcessesAsync(ct);
    return Results.Ok(result);
});

app.MapGet("/api/works", async (
    [FromQuery] string? workId,
    [FromQuery] string? model,
    [FromQuery] string? serial,
    [FromQuery] string? process,
    [FromQuery] DateTimeOffset? from,
    [FromQuery] DateTimeOffset? to,
    IWorkQueryPort port,
    CancellationToken ct) =>
{
    var query = new WorkSearchQuery(workId, model, serial, process, from, to);
    var result = await port.SearchWorksAsync(query, ct);
    return Results.Ok(result);
});

app.MapGet("/api/works/{workId}", async (string workId, IWorkQueryPort port, CancellationToken ct) =>
{
    var detail = await port.GetWorkDetailAsync(workId, ct);
    return detail is null
        ? Results.NotFound(new { error = "NOT_FOUND", message = "work not found" })
        : Results.Ok(detail);
});

app.MapGet("/api/segments/{segmentId:guid}/download", async (Guid segmentId, ISegmentDeliveryPort delivery, CancellationToken ct) =>
{
    var result = await delivery.GetSegmentAsync(segmentId, true, ct);
    if (result is null) return Results.NotFound(new { error = "NOT_FOUND", message = "segment not found" });

    var fileName = BuildFileName(result.Segment);
    return Results.File(result.Stream, result.ContentType, fileName, enableRangeProcessing: true, lastModified: result.LastModified);
});

app.MapGet("/api/segments/{segmentId:guid}/stream", async (Guid segmentId, ISegmentDeliveryPort delivery, CancellationToken ct) =>
{
    var result = await delivery.GetSegmentAsync(segmentId, false, ct);
    if (result is null) return Results.NotFound(new { error = "NOT_FOUND", message = "segment not found" });

    return Results.File(result.Stream, result.ContentType, enableRangeProcessing: true, lastModified: result.LastModified);
});

app.MapPost("/api/tusd/hooks/completed", async (
    HttpContext context,
    UploadCompletedRequest request,
    IIngestionAndMaintenancePort useCase,
    IOptions<SecurityOptions> security,
    ILoggerFactory loggerFactory,
    CancellationToken ct) =>
{
    var logger = loggerFactory.CreateLogger("UploadCompleted");
    if (!string.IsNullOrWhiteSpace(security.Value.TusdHookApiKey))
    {
        if (!context.Request.Headers.TryGetValue("X-Api-Key", out var header) || !string.Equals(header, security.Value.TusdHookApiKey, StringComparison.Ordinal))
        {
            return Results.Unauthorized();
        }
    }

    if (!UploadCompletedMapper.TryMap(request, out var model, out var error))
    {
        return Results.BadRequest(new { error = "INVALID_REQUEST", message = error });
    }

    try
    {
        var result = await useCase.HandleUploadCompletedAsync(model, ct);
        return Results.Ok(new { result.SegmentId, result.SegmentUuid, result.WorkId, result.LocalPath, result.WasUpdated });
    }
    catch (FileNotFoundException ex)
    {
        logger.LogWarning(ex, "Incoming file missing: {Path}", ex.FileName);
        return Results.BadRequest(new { error = "FILE_NOT_FOUND", message = ex.Message });
    }
    catch (Exception ex)
    {
        logger.LogError(ex, "Failed to handle upload completed");
        return Results.StatusCode(StatusCodes.Status500InternalServerError);
    }
});

app.MapPost("/api/jobs/cleanup", async (IIngestionAndMaintenancePort useCase, IOptions<MaintenanceOptions> maintenance, CancellationToken ct) =>
{
    var retention = TimeSpan.FromDays(maintenance.Value.RetentionDays);
    var count = await useCase.RunCleanupAsync(retention, maintenance.Value.AllowDeleteUnarchived, ct);
    return Results.Ok(new { deleted = count });
});

app.MapPost("/api/jobs/archive", async (IIngestionAndMaintenancePort useCase, CancellationToken ct) =>
{
    var count = await useCase.RunArchiveAsync(ct);
    return Results.Ok(new { archived = count });
});

app.Run();

static string BuildFileName(SegmentView segment)
{
    var sanitizedWork = string.Concat(segment.WorkId.Select(ch => Path.GetInvalidFileNameChars().Contains(ch) ? '-' : ch));
    return $"{sanitizedWork}-{segment.SegmentIndex}-{segment.SegmentUuid}.mp4";
}
