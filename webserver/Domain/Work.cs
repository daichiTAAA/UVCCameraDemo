namespace WebServer.Domain;

public sealed class Work
{
    public string WorkId { get; init; } = string.Empty;
    public string Model { get; init; } = string.Empty;
    public string Serial { get; init; } = string.Empty;
    public string Process { get; init; } = string.Empty;
}
