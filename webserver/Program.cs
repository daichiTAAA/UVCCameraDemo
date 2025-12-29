using Dapper;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.FileProviders;
using Microsoft.Extensions.Options;
using WebServer.Api;
using WebServer.Api.Requests;
using WebServer.Application.Models;
using WebServer.Application.Ports;
using WebServer.Application.UseCases;
using WebServer.Configuration;
using WebServer.Infrastructure;

var builder = WebApplication.CreateBuilder(args);

// Ensure snake_case DB columns map to PascalCase properties (e.g. segment_id -> SegmentId).
DefaultTypeMap.MatchNamesWithUnderscores = true;

builder.Services.Configure<StorageOptions>(builder.Configuration.GetSection("Storage"));
builder.Services.Configure<SecurityOptions>(builder.Configuration.GetSection("Security"));
builder.Services.Configure<LifecycleOptions>(builder.Configuration.GetSection("Lifecycle"));
builder.Services.Configure<AdlsOptions>(builder.Configuration.GetSection("Adls"));

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

builder.Services.AddSingleton<IClockPort, SystemClock>();
builder.Services.AddSingleton<IMetadataStorePort>(sp =>
{
    var configuration = sp.GetRequiredService<IConfiguration>();
    var connectionString = configuration.GetConnectionString("Main");
    if (string.IsNullOrWhiteSpace(connectionString))
    {
        throw new InvalidOperationException("ConnectionStrings:Main is required (PostgreSQL)");
    }

    var store = new PostgresMetadataStore(connectionString, sp.GetRequiredService<ILogger<PostgresMetadataStore>>());
    store.InitializeAsync(CancellationToken.None).GetAwaiter().GetResult();
    return store;
});
builder.Services.AddSingleton<IVideoStoragePort>(sp => new FileSystemVideoStorage(sp.GetRequiredService<IOptions<StorageOptions>>().Value));
builder.Services.AddSingleton<IArchiveStoragePort>(sp => new AdlsBlobStorage(sp.GetRequiredService<IOptions<AdlsOptions>>().Value, sp.GetRequiredService<ILogger<AdlsBlobStorage>>()));
builder.Services.AddSingleton<IMetadataQueryPort, MetadataQueryService>();
builder.Services.AddSingleton<ISegmentDeliveryPort, SegmentDeliveryService>();
builder.Services.AddSingleton<IIngestionAndLifecyclePort, IngestionAndLifecycleService>();
builder.Services.AddHostedService<LifecycleHostedService>();

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseMiddleware<ApiKeyMiddleware>();

var staticRoot = builder.Configuration.GetSection("StaticFiles").GetValue<string>("RootPath");
if (!string.IsNullOrWhiteSpace(staticRoot))
{
    var fullPath = Path.GetFullPath(Path.Combine(builder.Environment.ContentRootPath, staticRoot));
    if (Directory.Exists(fullPath))
    {
        var provider = new PhysicalFileProvider(fullPath);
        app.UseDefaultFiles(new DefaultFilesOptions { FileProvider = provider });
        app.UseStaticFiles(new StaticFileOptions { FileProvider = provider });
        app.MapFallback(() => Results.File(Path.Combine(fullPath, "index.html"), "text/html"));
    }
}

app.MapGet("/api/health", () => Results.Ok(new { status = "ok" }));

app.MapGet("/api/processes", async (IMetadataQueryPort port, CancellationToken ct) =>
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
    IMetadataQueryPort port,
    CancellationToken ct) =>
{
    var query = new WorkSearchQuery(workId, model, serial, process, from, to);
    var result = await port.SearchWorksAsync(query, ct);
    return Results.Ok(result);
});

app.MapGet("/api/works/{workId}", async (string workId, IMetadataQueryPort port, CancellationToken ct) =>
{
    var detail = await port.GetWorkDetailAsync(workId, ct);
    return detail is null
        ? Results.NotFound(new { error = "NOT_FOUND", message = "work not found" })
        : Results.Ok(detail);
});

app.MapGet("/api/segments/{segmentId:guid}/download", (Guid segmentId, ISegmentDeliveryPort delivery) =>
{
    return new SegmentContentResult(segmentId, asAttachment: true, delivery);
});

app.MapGet("/api/segments/{segmentId:guid}/stream", (Guid segmentId, ISegmentDeliveryPort delivery) =>
{
    return new SegmentContentResult(segmentId, asAttachment: false, delivery);
});

app.MapPost("/api/tusd/hooks/completed", async (
    HttpContext context,
    UploadCompletedRequest request,
    IIngestionAndLifecyclePort useCase,
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

app.MapPost("/api/jobs/cleanup", async (IIngestionAndLifecyclePort useCase, IOptions<LifecycleOptions> lifecycle, CancellationToken ct) =>
{
    var retention = TimeSpan.FromDays(lifecycle.Value.RetentionDays);
    var count = await useCase.RunCleanupAsync(retention, lifecycle.Value.AllowDeleteUnarchived, ct);
    return Results.Ok(new { deleted = count });
});

app.MapPost("/api/jobs/archive", async (IIngestionAndLifecyclePort useCase, CancellationToken ct) =>
{
    var count = await useCase.RunArchiveAsync(ct);
    return Results.Ok(new { archived = count });
});

app.Run();
