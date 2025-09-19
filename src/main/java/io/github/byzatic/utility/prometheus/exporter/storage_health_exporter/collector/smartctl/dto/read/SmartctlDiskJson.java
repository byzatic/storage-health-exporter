package io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.smartctl.dto.read;

import java.util.List;

public class SmartctlDiskJson {
    public List<Integer> json_format_version;
    public String model_name;
    public String serial_number;
    public SmartStatus smart_status;
    public Temperature temperature;
    public PowerOnTime power_on_time;
    public SmartAttributes ata_smart_attributes;

    public static class SmartStatus {
        public boolean passed;
    }

    public static class Temperature {
        public int current;
    }

    public static class PowerOnTime {
        public int hours;
    }

    public static class SmartAttributes {
        public List<Attribute> table;
    }

    public static class Attribute {
        public int id;
        public String name;
        public RawValue raw;
    }

    public static class RawValue {
        public long value;
    }
}
