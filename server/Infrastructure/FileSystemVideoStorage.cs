using Microsoft.AspNetCore.StaticFiles;
using WebServer.Application.Ports;
using WebServer.Configuration;

namespace WebServer.Infrastructure;

public sealed class FileSystemVideoStorage : IVideoStoragePort
{
    private readonly StorageOptions options;
    private readonly FileExtensionContentTypeProvider contentTypeProvider = new();

    public FileSystemVideoStorage(StorageOptions options)
    {
        this.options = options;
        Directory.CreateDirectory(options.LocalRoot);
        var incoming = Path.Combine(options.LocalRoot, options.IncomingSubdirectory);
        Directory.CreateDirectory(incoming);
    }

    public Task<bool> ExistsAsync(string localPath, CancellationToken ct)
    {
        return Task.FromResult(File.Exists(localPath));
    }

    public Task<bool> RemoveAsync(string localPath, CancellationToken ct)
    {
        try
        {
            if (!File.Exists(localPath)) return Task.FromResult(false);
            File.Delete(localPath);
            return Task.FromResult(true);
        }
        catch
        {
            return Task.FromResult(false);
        }
    }

    public Task<VideoReadHandle?> TryOpenAsync(string localPath, CancellationToken ct)
    {
        if (!File.Exists(localPath))
        {
            return Task.FromResult<VideoReadHandle?>(null);
        }

        var contentType = contentTypeProvider.TryGetContentType(localPath, out var ctValue) ? ctValue : "application/octet-stream";
        var lastWrite = File.GetLastWriteTimeUtc(localPath);
        DateTimeOffset? lastModified = lastWrite == DateTime.MinValue ? null : new DateTimeOffset(lastWrite, TimeSpan.Zero);

        var stream = new FileStream(localPath, FileMode.Open, FileAccess.Read, FileShare.Read, 64 * 1024, FileOptions.Asynchronous);
        return Task.FromResult<VideoReadHandle?>(new VideoReadHandle(stream, stream.Length, contentType, lastModified));
    }

    public Task<string> PromoteIncomingAsync(string sourcePath, string workId, Guid segmentUuid, CancellationToken ct)
    {
        var fullSource = Path.GetFullPath(sourcePath);
        if (!File.Exists(fullSource))
        {
            throw new FileNotFoundException("Incoming file not found", fullSource);
        }

        var workDir = Path.Combine(options.LocalRoot, workId);
        Directory.CreateDirectory(workDir);
        var targetName = $"{segmentUuid}.mp4";
        var targetPath = Path.Combine(workDir, targetName);

        if (string.Equals(fullSource, targetPath, StringComparison.Ordinal))
        {
            return Task.FromResult(targetPath);
        }

        File.Move(fullSource, targetPath, overwrite: true);
        return Task.FromResult(targetPath);
    }
}
