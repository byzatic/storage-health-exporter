package io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector;

import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.smartctl.SmartCTLReader;
import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.model.MegaRAIDDiskInfo;
import io.prometheus.metrics.core.metrics.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RAIDMetricsCollectorWithCaching implements RAIDMetricsCollectorInterface {
    private static final Logger logger = LoggerFactory.getLogger(RAIDMetricsCollector.class);

    private final SmartCTLReader reader;

    // Храним последние использованные labels по ключу диска (serial или diskId)
    private final Map<String, String[]> knownLabelsByKey = new ConcurrentHashMap<>();

    private static final Gauge reallocatedSectors = Gauge.builder()
            .name("reallocated_sectors")
            .help("Reallocated sectors count per disk")
            .labelNames("disk_id", "model", "serial", "mount_point")
            .register();

    private static final Gauge powerOnHours = Gauge.builder()
            .name("power_on_hours")
            .help("Power on hours per disk")
            .labelNames("disk_id", "model", "serial", "mount_point")
            .register();

    private static final Gauge temperatureCelsius = Gauge.builder()
            .name("temperature_celsius")
            .help("Disk temperature in Celsius")
            .labelNames("disk_id", "model", "serial", "mount_point")
            .register();

    private static final Gauge currentPendingSectors = Gauge.builder()
            .name("current_pending_sectors")
            .help("Current pending sectors")
            .labelNames("disk_id", "model", "serial", "mount_point")
            .register();

    private static final Gauge offlineUncorrectable = Gauge.builder()
            .name("offline_uncorrectable")
            .help("Offline uncorrectable sectors")
            .labelNames("disk_id", "model", "serial", "mount_point")
            .register();

    private static final Gauge udmaCrcErrors = Gauge.builder()
            .name("udma_crc_errors")
            .help("UDMA CRC error count")
            .labelNames("disk_id", "model", "serial", "mount_point")
            .register();

    private static final Gauge smartPassed = Gauge.builder()
            .name("smart_passed")
            .help("SMART overall health passed status (1=PASSED, 0=FAILED)")
            .labelNames("disk_id", "model", "serial", "device_name")
            .register();

    public RAIDMetricsCollectorWithCaching(SmartCTLReader reader) {
        this.reader = reader;
    }

    @Override
    public synchronized void updateMetrics() {
        try {
            List<MegaRAIDDiskInfo> disks = reader.readDisks();

            // Ключи, которые встретились в этом апдейте
            Set<String> seenKeys = new HashSet<>(disks.size());

            for (MegaRAIDDiskInfo disk : disks) {
                String key = buildKey(disk); // приоритет serial, иначе diskId
                seenKeys.add(key);

                String[] newLabels = buildLabels(disk);

                // Если раньше были другие labels для этого ключа — удаляем старые с всех метрик
                String[] oldLabels = knownLabelsByKey.get(key);
                if (oldLabels != null && !Arrays.equals(oldLabels, newLabels)) {
                    removeAllMetricsFor(oldLabels);
                }

                // Апдейтим значения (устанавливаем новые labels/values)
                smartPassed.labelValues(newLabels)
                        .set("PASSED".equalsIgnoreCase(nullToEmpty(disk.smartStatus)) ? 1 : 0);
                reallocatedSectors.labelValues(newLabels).set(disk.reallocatedSectors);
                powerOnHours.labelValues(newLabels).set(disk.powerOnHours);
                temperatureCelsius.labelValues(newLabels).set(disk.temperatureCelsius);
                currentPendingSectors.labelValues(newLabels).set(disk.currentPendingSectors);
                offlineUncorrectable.labelValues(newLabels).set(disk.offlineUncorrectable);
                udmaCrcErrors.labelValues(newLabels).set(disk.udmaCrcErrors);

                // Запоминаем актуальные labels для ключа
                knownLabelsByKey.put(key, newLabels);
            }

            // Удаляем метрики для дисков, которых больше нет
            Iterator<Map.Entry<String, String[]>> it = knownLabelsByKey.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String[]> entry = it.next();
                if (!seenKeys.contains(entry.getKey())) {
                    removeAllMetricsFor(entry.getValue());
                    it.remove();
                }
            }

        } catch (Exception e) {
            logger.error("Failed to update RAID metrics", e);
            throw new RuntimeException(e);
        }
    }

    private static String buildKey(MegaRAIDDiskInfo d) {
        String serial = nullToEmpty(d.serial);
        if (!serial.isEmpty()) return "S:" + serial;
        return "D:" + d.diskId; // fallback
    }

    // Порядок значений должен соответствовать порядку labelNames у всех Gauge
    // Имена различаются ("mount_point" vs "device_name"), но значения совпадают по позиции.
    private static String[] buildLabels(MegaRAIDDiskInfo d) {
        return new String[] {
                String.valueOf(d.diskId),
                defaultIfEmpty(d.model, "unknown"),
                defaultIfEmpty(d.serial, "unknown"),
                defaultIfEmpty(d.deviceName, "<not mounted>")
        };
    }

    private static void removeAllMetricsFor(String[] labels) {
        // remove не «моргает»: удаляем конкретную комбинацию меток
        reallocatedSectors.remove(labels);
        powerOnHours.remove(labels);
        temperatureCelsius.remove(labels);
        currentPendingSectors.remove(labels);
        offlineUncorrectable.remove(labels);
        udmaCrcErrors.remove(labels);
        smartPassed.remove(labels);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String defaultIfEmpty(String s, String def) {
        return (s == null || s.isEmpty()) ? def : s;
    }
}