namespace WebServer.Configuration;

public sealed class SecurityOptions
{
    /// <summary>
    /// Optional API key required via X-Api-Key. Empty disables the check.
    /// </summary>
    public string ApiKey { get; set; } = string.Empty;

    /// <summary>
    /// Optional API key for tusd hooks. If empty, falls back to ApiKey.
    /// </summary>
    public string TusdHookApiKey { get; set; } = string.Empty;
}
