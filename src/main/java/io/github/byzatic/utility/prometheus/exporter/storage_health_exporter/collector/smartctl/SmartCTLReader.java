package io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.smartctl;

import com.google.gson.Gson;
import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.exceptions.CollectorException;
import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.smartctl.dto.read.SmartctlDiskJson;
import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.smartctl.dto.scan.SmartctlDevice;
import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.smartctl.dto.scan.SmartctlScanResult;
import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.model.MegaRAIDDiskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class SmartCTLReader {
    private final static Logger logger = LoggerFactory.getLogger(SmartCTLReader.class);

    public List<MegaRAIDDiskInfo> readDisks() throws CollectorException {
        List<MegaRAIDDiskInfo> disks = new ArrayList<>();
        List<DeviceEntry> allDevices = scanDevices();

        boolean hasMegaRAID = allDevices.stream().anyMatch(dev -> dev.driver.startsWith("megaraid"));
        logger.debug("hasMegaRAID is {}", hasMegaRAID);

        for (DeviceEntry device : allDevices) {
            logger.debug("process device: {}", device);
            if (hasMegaRAID && !device.driver.startsWith("megaraid")) {
                logger.debug("Skip regular device");
                continue;
            }

            try {
                List<String> cmd = new ArrayList<>();
                cmd.add("smartctl");
                cmd.add("-a");
                cmd.add("-j");
                if (device.driver.startsWith("megaraid")) {
                    cmd.add("-d");
                    cmd.add(device.driver);
                }
                cmd.add(device.dev);

                logger.debug("try to run {}", cmd);
                Process process = new ProcessBuilder(cmd).start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder jsonBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }
                process.waitFor();

                SmartctlDiskJson json = new Gson().fromJson(jsonBuilder.toString(), SmartctlDiskJson.class);

                // проверка формата
                if (json.json_format_version == null || json.json_format_version.size() != 2 ||
                        json.json_format_version.get(0) != 1 || json.json_format_version.get(1) != 0) {
                    throw new CollectorException("Unsupported smartctl json_format_version");
                }

                MegaRAIDDiskInfo disk = new MegaRAIDDiskInfo();
                disk.diskId = extractMegaRAIDIndex(device.driver);
                disk.deviceName = device.dev;
                disk.model = json.model_name;
                disk.serial = json.serial_number;
                disk.smartStatus = json.smart_status != null && json.smart_status.passed ? "PASSED" : "FAILED";
                disk.temperatureCelsius = json.temperature != null ? json.temperature.current : -1;
                disk.powerOnHours = json.power_on_time != null ? json.power_on_time.hours : -1;
                disk.reallocatedSectors = getRawValue(json, "Reallocated_Sector_Ct");
                disk.currentPendingSectors = getRawValue(json, "Current_Pending_Sector");
                disk.offlineUncorrectable = getRawValue(json, "Offline_Uncorrectable");
                disk.udmaCrcErrors = getRawValue(json, "UDMA_CRC_Error_Count");

                disks.add(disk);
                logger.debug("Device {} parsed successfully", device);

            } catch (Exception e) {
                logger.warn("Failed to parse device {}: {}", device, e.getMessage());
            }
        }

        return disks;
    }

    private long getRawValue(SmartctlDiskJson json, String name) {
        if (json.ata_smart_attributes != null && json.ata_smart_attributes.table != null) {
            return json.ata_smart_attributes.table.stream()
                    .filter(attr -> name.equals(attr.name))
                    .findFirst()
                    .map(attr -> attr.raw != null ? attr.raw.value : -1)
                    .orElse(-1L);
        }
        return -1L;
    }

    private List<DeviceEntry> scanDevices() throws CollectorException {
        logger.debug("Starts scan devices");
        List<DeviceEntry> devices = new ArrayList<>();

        try {
            Process process = new ProcessBuilder("smartctl", "--scan", "-j").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // Чтение всего JSON в строку
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            process.waitFor();

            String json = jsonBuilder.toString();
            Gson gson = new Gson();

            SmartctlScanResult result = gson.fromJson(json, SmartctlScanResult.class);

            // Валидация версии JSON
            if (result.json_format_version == null || result.json_format_version.size() != 2 ||
                    result.json_format_version.get(0) != 1 || result.json_format_version.get(1) != 0) {
                throw new CollectorException("Unsupported or missing json_format_version: " + result.json_format_version);
            }

            // Валидация устройств
            if (result.devices == null || result.devices.isEmpty()) {
                throw new CollectorException("No devices found in smartctl JSON output");
            }

            for (SmartctlDevice device : result.devices) {
                if (device.name == null || device.type == null) {
                    throw new CollectorException("Invalid device entry: " + device);
                }
                devices.add(new DeviceEntry(device.name, device.type));
            }

        } catch (Exception e) {
            throw new CollectorException("Failed to parse smartctl --scan -j output", e);
        }

        logger.debug("Scan devices result: {}", devices);
        return devices;
    }

    private int extractMegaRAIDIndex(String driver) {
        if (driver.startsWith("megaraid,")) {
            try {
                return Integer.parseInt(driver.substring("megaraid,".length()));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private static class DeviceEntry {
        String dev;
        String driver;

        DeviceEntry(String dev, String driver) {
            this.dev = dev;
            this.driver = driver;
        }

        @Override
        public String toString() {
            return "DeviceEntry{" +
                    "dev='" + dev + '\'' +
                    ", driver='" + driver + '\'' +
                    '}';
        }
    }
}