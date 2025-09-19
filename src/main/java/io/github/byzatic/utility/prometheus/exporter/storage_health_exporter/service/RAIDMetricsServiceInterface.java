package io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.service;

import java.io.IOException;

public interface RAIDMetricsServiceInterface {
    void run() throws IOException;

    void terminate();
}
