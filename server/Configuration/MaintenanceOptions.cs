namespace WebServer.Configuration;

public sealed class MaintenanceOptions
{
    /// <summary>
    /// Retention in days for local videos (receivedAt baseline).
    /// </summary>
    public int RetentionDays { get; set; } = 7;

    /// <summary>
    /// When true, allows deleting segments even if not archived. Default false for safety.
    /// </summary>
    public bool AllowDeleteUnarchived { get; set; } = false;

    /// <summary>
    /// Background maintenance interval in minutes.
    /// </summary>
    public int BackgroundIntervalMinutes { get; set; } = 60;
}
