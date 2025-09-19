package io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.model;

public class MegaRAIDDiskInfo {
    public int diskId;
    public String model;
    public String serial;
    public String deviceName;
    public String smartStatus;
    public long reallocatedSectors;
    public int powerOnHours;
    public int temperatureCelsius;
    public long currentPendingSectors;
    public long offlineUncorrectable;
    public long udmaCrcErrors;
}
