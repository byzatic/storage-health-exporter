package io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector;

import org.jetbrains.annotations.NotNull;

public interface SmartctlCollectorFactoryInterface {
    @NotNull RAIDMetricsCollectorInterface getCollector(@NotNull Boolean caching);
}
