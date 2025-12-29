namespace WebServer.Configuration;

public sealed class AdlsOptions
{
    /// <summary>
    /// Enables ADLS/Blob archive + fallback delivery.
    /// </summary>
    public bool Enabled { get; set; } = false;

    /// <summary>
    /// Storage account base URL. Accepts either blob or dfs endpoint.
    /// Example: https://&lt;account&gt;.blob.core.windows.net/
    /// </summary>
    public string AccountUrl { get; set; } = string.Empty;

    /// <summary>
    /// Container (file system) name.
    /// </summary>
    public string Container { get; set; } = string.Empty;

    /// <summary>
    /// Blob prefix under the container (default: videos).
    /// Stored paths will be like: {Prefix}/YYYY/MM/DD/&lt;workId&gt;/&lt;segmentUuid&gt;.mp4
    /// </summary>
    public string Prefix { get; set; } = "videos";

    /// <summary>
    /// Optional SAS token for accessing the storage account.
    /// When set, the server uses SAS authentication instead of DefaultAzureCredential.
    /// Provide without leading '?', e.g. "sv=...&sig=...".
    /// </summary>
    public string SasToken { get; set; } = string.Empty;
}
