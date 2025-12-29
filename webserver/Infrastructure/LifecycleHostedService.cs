using Microsoft.Extensions.Options;
using WebServer.Application.Ports;
using WebServer.Configuration;

namespace WebServer.Infrastructure;

public sealed class LifecycleHostedService : BackgroundService
{
    private readonly IServiceProvider services;
    private readonly ILogger<LifecycleHostedService> logger;
    private readonly IOptionsMonitor<LifecycleOptions> options;

    public LifecycleHostedService(IServiceProvider services, ILogger<LifecycleHostedService> logger, IOptionsMonitor<LifecycleOptions> options)
    {
        this.services = services;
        this.logger = logger;
        this.options = options;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            var intervalMinutes = Math.Max(5, options.CurrentValue.BackgroundIntervalMinutes);
            var retention = TimeSpan.FromDays(options.CurrentValue.RetentionDays);

            try
            {
                using var scope = services.CreateScope();
                var useCase = scope.ServiceProvider.GetRequiredService<IIngestionAndLifecyclePort>();
                await useCase.RunCleanupAsync(retention, options.CurrentValue.AllowDeleteUnarchived, stoppingToken);
                await useCase.RunArchiveAsync(stoppingToken);
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Lifecycle job failed");
            }

            try
            {
                await Task.Delay(TimeSpan.FromMinutes(intervalMinutes), stoppingToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }
    }
}
