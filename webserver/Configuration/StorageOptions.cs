namespace WebServer.Configuration;

public sealed class StorageOptions
{
    public string LocalRoot { get; set; } = "data/videos";
    public string IncomingSubdirectory { get; set; } = "incoming";
    public string MetadataPath { get; set; } = "data/metadata.json";
}
