using Microsoft.Extensions.Options;
using WebServer.Application.Ports;
using WebServer.Configuration;

namespace WebServer.Infrastructure;

public sealed class MaintenanceHostedService : BackgroundService
{
    private readonly IServiceProvider services;
    private readonly ILogger<MaintenanceHostedService> logger;
    private readonly IOptionsMonitor<MaintenanceOptions> options;

    public MaintenanceHostedService(IServiceProvider services, ILogger<MaintenanceHostedService> logger, IOptionsMonitor<MaintenanceOptions> options)
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
                var useCase = scope.ServiceProvider.GetRequiredService<IIngestionAndMaintenancePort>();
                await useCase.RunCleanupAsync(retention, options.CurrentValue.AllowDeleteUnarchived, stoppingToken);
                await useCase.RunArchiveAsync(stoppingToken);
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Maintenance job failed");
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
