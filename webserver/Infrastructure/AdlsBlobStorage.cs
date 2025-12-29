using Azure;
using Azure.Identity;
using Azure.Storage.Blobs;
using Azure.Storage.Blobs.Models;
using WebServer.Application.Ports;
using WebServer.Configuration;

namespace WebServer.Infrastructure;

public sealed class AdlsBlobStorage : IArchiveStoragePort
{
    private readonly AdlsOptions options;
    private readonly ILogger<AdlsBlobStorage> logger;
    private readonly BlobContainerClient? container;

    public AdlsBlobStorage(AdlsOptions options, ILogger<AdlsBlobStorage> logger)
    {
        this.options = options;
        this.logger = logger;

        if (!options.Enabled)
        {
            container = null;
            return;
        }

        if (string.IsNullOrWhiteSpace(options.AccountUrl) || string.IsNullOrWhiteSpace(options.Container))
        {
            logger.LogWarning("ADLS is enabled but AccountUrl/Container is missing; ADLS operations will be disabled");
            container = null;
            return;
        }

        var accountUrl = NormalizeAccountUrl(options.AccountUrl);
        BlobServiceClient service;
        var sasToken = NormalizeSasToken(options.SasToken);
        if (!string.IsNullOrWhiteSpace(sasToken))
        {
            // SAS token authentication (preferred for local/dev via .env).
            service = new BlobServiceClient(new Uri(accountUrl), new AzureSasCredential(sasToken));
        }
        else
        {
            // AAD authentication (Managed Identity / env vars / Azure CLI).
            service = new BlobServiceClient(new Uri(accountUrl), new DefaultAzureCredential());
        }
        container = service.GetBlobContainerClient(options.Container);
    }

    public async Task<ArchiveObjectProperties?> TryGetPropertiesAsync(string adlsPath, CancellationToken ct)
    {
        var blob = GetBlobClient(adlsPath);
        if (blob is null) return null;

        try
        {
            var props = await blob.GetPropertiesAsync(cancellationToken: ct);
            return new ArchiveObjectProperties(props.Value.ContentLength, props.Value.ContentType, props.Value.LastModified);
        }
        catch (RequestFailedException ex) when (ex.Status == 404)
        {
            return null;
        }
    }

    public async Task<Stream?> TryOpenReadAsync(string adlsPath, CancellationToken ct)
    {
        var blob = GetBlobClient(adlsPath);
        if (blob is null) return null;

        try
        {
            var response = await blob.DownloadStreamingAsync(cancellationToken: ct);
            return response.Value.Content;
        }
        catch (RequestFailedException ex) when (ex.Status == 404)
        {
            return null;
        }
    }

    public async Task<Stream?> TryOpenReadRangeAsync(string adlsPath, long offset, long length, CancellationToken ct)
    {
        var blob = GetBlobClient(adlsPath);
        if (blob is null) return null;

        try
        {
            var range = new HttpRange(offset, length);
            var options = new BlobDownloadOptions { Range = range };
            var response = await blob.DownloadStreamingAsync(options, ct);
            return response.Value.Content;
        }
        catch (RequestFailedException ex) when (ex.Status == 404)
        {
            return null;
        }
    }

    public async Task<bool> UploadAsync(string localPath, string adlsPath, CancellationToken ct)
    {
        var blob = GetBlobClient(adlsPath);
        if (blob is null) return false;

        if (!File.Exists(localPath))
        {
            logger.LogWarning("Local file not found for upload: {Path}", localPath);
            return false;
        }

        try
        {
            await container!.CreateIfNotExistsAsync(publicAccessType: PublicAccessType.None, cancellationToken: ct);
            await blob.UploadAsync(localPath, overwrite: true, cancellationToken: ct);
            return true;
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "Failed to upload to ADLS: {AdlsPath}", adlsPath);
            return false;
        }
    }

    private BlobClient? GetBlobClient(string adlsPath)
    {
        if (container is null) return null;
        var normalized = NormalizePath(adlsPath);
        if (string.IsNullOrWhiteSpace(normalized)) return null;
        return container.GetBlobClient(normalized);
    }

    private static string NormalizePath(string path)
    {
        var p = path.Trim();
        if (p.StartsWith('/')) p = p[1..];
        return p;
    }

    private static string NormalizeAccountUrl(string url)
    {
        var trimmed = url.TrimEnd('/');
        // If a dfs endpoint is provided, convert to blob endpoint for Blob SDK.
        if (trimmed.Contains(".dfs.core.windows.net", StringComparison.OrdinalIgnoreCase))
        {
            trimmed = trimmed.Replace(".dfs.core.windows.net", ".blob.core.windows.net", StringComparison.OrdinalIgnoreCase);
        }
        return trimmed;
    }

    private static string NormalizeSasToken(string token)
    {
        var t = token.Trim();
        if (t.StartsWith('?')) t = t[1..];
        return t;
    }
}
