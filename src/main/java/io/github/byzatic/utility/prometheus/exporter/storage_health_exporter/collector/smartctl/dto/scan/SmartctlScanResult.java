package io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.smartctl.dto.scan;

import java.util.List;

public class SmartctlScanResult {
    public List<Integer> json_format_version;
    public List<SmartctlDevice> devices;
}
