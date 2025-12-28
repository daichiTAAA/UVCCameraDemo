using Microsoft.Extensions.Options;
using WebServer.Configuration;

namespace WebServer.Infrastructure;

public sealed class ApiKeyMiddleware(RequestDelegate next, IOptions<SecurityOptions> options, ILogger<ApiKeyMiddleware> logger)
{
    public async Task InvokeAsync(HttpContext context)
    {
        var configured = options.Value.ApiKey;
        var tusdKey = options.Value.TusdHookApiKey;
        if (string.IsNullOrWhiteSpace(configured))
        {
            await next(context);
            return;
        }

        if (context.Request.Headers.TryGetValue("X-Api-Key", out var header) &&
            (string.Equals(header, configured, StringComparison.Ordinal) || (!string.IsNullOrWhiteSpace(tusdKey) && string.Equals(header, tusdKey, StringComparison.Ordinal))))
        {
            await next(context);
            return;
        }

        logger.LogWarning("Request rejected: missing or invalid API key");
        context.Response.StatusCode = StatusCodes.Status401Unauthorized;
        await context.Response.WriteAsJsonAsync(new { error = "UNAUTHORIZED", message = "Invalid API key" });
    }
}
