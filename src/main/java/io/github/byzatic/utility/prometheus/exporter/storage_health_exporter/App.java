package io.github.byzatic.utility.prometheus.exporter.storage_health_exporter;

import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.RAIDMetricsCollectorInterface;
import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.SmartctlCollectorFactory;
import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.SmartctlCollectorFactoryInterface;
import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.smartctl.SmartCTLReader;
import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.service.RAIDMetricsService;
import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.service.RAIDMetricsServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
    private final static Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        logger.debug("MegaRAID metrics service is running...");

        SmartctlCollectorFactoryInterface collectorFactory = new SmartctlCollectorFactory(new SmartCTLReader());
        RAIDMetricsCollectorInterface collector = collectorFactory.getCollector(Configuration.FEATURE_FLAG_CACHING_COLLECTOR);
        RAIDMetricsServiceInterface megaRAIDMetricsService = new RAIDMetricsService(Configuration.PROMETHEUS_URL, Configuration.CRON_EXPRESSION_STRING, collector);

        try {
            megaRAIDMetricsService.run();
        } catch (Exception e) {
            megaRAIDMetricsService.terminate();
            logger.error("An error was occurred: {}", e.getMessage());
            logger.debug("Stacktrace: ", e);
            throw new RuntimeException(e);
        }

    }
}
