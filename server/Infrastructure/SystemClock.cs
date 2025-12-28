using WebServer.Application.Ports;

namespace WebServer.Infrastructure;

public sealed class SystemClock : IClockPort
{
    public DateTimeOffset UtcNow => DateTimeOffset.UtcNow;
}
