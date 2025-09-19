package io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector;

import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.smartctl.SmartCTLReader;
import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.model.MegaRAIDDiskInfo;
import io.prometheus.metrics.core.metrics.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RAIDMetricsCollector implements RAIDMetricsCollectorInterface {
    private final static Logger logger = LoggerFactory.getLogger(RAIDMetricsCollector.class);

    private final SmartCTLReader reader;

    private static final Gauge reallocatedSectors = Gauge.builder()
            .name("megaraid_reallocated_sectors")
            .help("Reallocated sectors count per disk")
            .labelNames("disk_id", "model", "serial", "mount_point")
            .register();

    private static final Gauge powerOnHours = Gauge.builder()
            .name("megaraid_power_on_hours")
            .help("Power on hours per disk")
            .labelNames("disk_id", "model", "serial", "mount_point")
            .register();

    private static final Gauge temperatureCelsius = Gauge.builder()
            .name("megaraid_temperature_celsius")
            .help("Disk temperature in Celsius")
            .labelNames("disk_id", "model", "serial", "mount_point")
            .register();

    private static final Gauge currentPendingSectors = Gauge.builder()
            .name("megaraid_current_pending_sectors")
            .help("Current pending sectors")
            .labelNames("disk_id", "model", "serial", "mount_point")
            .register();

    private static final Gauge offlineUncorrectable = Gauge.builder()
            .name("megaraid_offline_uncorrectable")
            .help("Offline uncorrectable sectors")
            .labelNames("disk_id", "model", "serial", "mount_point")
            .register();

    private static final Gauge udmaCrcErrors = Gauge.builder()
            .name("megaraid_udma_crc_errors")
            .help("UDMA CRC error count")
            .labelNames("disk_id", "model", "serial", "mount_point")
            .register();

    private static final Gauge smartPassed = Gauge.builder()
            .name("megaraid_smart_passed")
            .help("SMART overall health passed status (1=PASSED, 0=FAILED)")
            .labelNames("disk_id", "model", "serial", "device_name")
            .register();

    public RAIDMetricsCollector(SmartCTLReader reader) {
        this.reader = reader;
    }

    @Override
    public void updateMetrics() {
        try {
            List<MegaRAIDDiskInfo> disks = reader.readDisks();
            for (MegaRAIDDiskInfo disk : disks) {
                String[] labels = {
                        String.valueOf(disk.diskId),
                        disk.model != null ? disk.model : "unknown",
                        disk.serial != null ? disk.serial : "unknown",
                        disk.deviceName != null ? disk.deviceName : "<not mounted>"
                };

                smartPassed.labelValues(labels).set("PASSED".equalsIgnoreCase(disk.smartStatus) ? 1 : 0);
                reallocatedSectors.labelValues(labels).set(disk.reallocatedSectors);
                powerOnHours.labelValues(labels).set(disk.powerOnHours);
                temperatureCelsius.labelValues(labels).set(disk.temperatureCelsius);
                currentPendingSectors.labelValues(labels).set(disk.currentPendingSectors);
                offlineUncorrectable.labelValues(labels).set(disk.offlineUncorrectable);
                udmaCrcErrors.labelValues(labels).set(disk.udmaCrcErrors);
            }
        } catch (Exception e) {
            logger.error("Failed to update RAID metrics", e);
            throw new RuntimeException(e);
        }
    }
}
