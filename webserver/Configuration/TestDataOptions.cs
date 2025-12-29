namespace WebServer.Configuration;

public sealed class TestDataOptions
{
    public bool Enabled { get; set; }

    /// <summary>
    /// Optional process names to seed. When empty and Enabled=true, a small default set is used.
    /// </summary>
    public string[] Processes { get; set; } = Array.Empty<string>();
}
