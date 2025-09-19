# Storage Health Exporter
Docker Raid Metrics Prometheus Exporter is a Java-based exporter that collects and exposes disk health and performance metrics for Prometheus via smartmontools. It supports both standalone drives (S.M.A.R.T.) and hardware RAID arrays—including controllers like LSI MegaRAID—so you can track mixed environments with a single tool. Delivered as a Docker container, it integrates cleanly into Prometheus/Grafana stacks for monitoring and alerting.

---

## Running with Docker

This project provides simple scripts and a ready-to-use `docker-compose.yml` for launching the exporter.

### Prerequisites

- Docker
- Prometheus scraping `http://<host>:20222/metrics`

### Usage

#### Start the container

```bash
./docker.up.sh
```

#### View logs

```bash
./docker.logs.sh
```

#### Stop the container

```bash
./docker.down.sh
```

### docker-compose.yml

```yaml
version: '3.7'
services:
  raidmetrics-exporter:
    image: byzatic/storage-health-exporter:latest
    container_name: storage-health-exporter
    privileged: true
    environment:
      - XMS=20m
      - XMX=1024m
    ports:
      - "20222:8080"
    volumes:
      #- ${PWD}/configuration:/app/configuration
      - ${PWD}/logs:/app/logs
    logging:
      driver: json-file
      options:
        max-file: "10"
        max-size: "10m"
    networks:
      - raidmetrics-net

networks:
  raidmetrics-net:
    name: raidmetrics-net
    external: false
```

---

## Application Parameters

```xml
<Configuration>
    <cronExpressionString>*/5 * * * * *</cronExpressionString>
    <prometheusEndpointURL>http://0.0.0.0:8080/metrics</prometheusEndpointURL>
</Configuration>
```

- **cronExpressionString**: Defines scheduling interval. Default: `*/5 * * * * *`
- **prometheusEndpointURL**: Prometheus-compatible HTTP endpoint. Default: `http://0.0.0.0:8080/metrics`
- **featureFlagCachingCollector**: enables caching mode that refreshes and removes outdated disk metrics to keep only current ones.

> The featureFlagCachingCollector parameter enables the caching collector mode: when set to true, the exporter maintains a cache of disks and refreshes metrics on each update, removing outdated entries. If a disk’s labels (model, deviceName/mount_point) change, the old label set is deleted and replaced with the new one, and if a disk disappears, all its metrics are removed. Ensuring that only current and valid metrics are exposed.

---

## Cron Syntax

Format: 5 or 6 space-separated fields:
```
[seconds] minutes hours day-of-month month day-of-week
```
- If only 5 fields are provided, the seconds field defaults to 0.

Supported tokens per field:
- `*` (any), exact numbers (e.g., `5`), ranges (`a-b`), lists (`a,b,c`),
- steps (`*/n`), and stepped ranges (`a-b/n`).

Notes:
- Names (`JAN–DEC`, `SUN–SAT`) and Quartz-specific tokens (`?`, `L`, `W`, `#`) are **NOT supported**.
- Day-of-Month **AND** Day-of-Week must both match (AND semantics).
- Day-of-Week uses `0–6`, where `0 = Sunday`.

Examples:
- `*/10 * * * * *` → every 10 seconds
- `0 */5 * * * *` → every 5 minutes (on second 0)
- `0 15 10 * * *` → 10:15:00 every day
- `0 0 12 * * 1-5` → 12:00:00 Monday–Friday (0=Sun…6=Sat)

The Quartz-style value `*/10 * * * * ?` is **NOT valid** here.  
Use the 6-field form `*/10 * * * * *` to run every 10 seconds.

---

## Metrics Interpretation

The exporter provides the following Prometheus metrics (one label per disk):

- **disk_status** → `1` if disk is OK, `0` if failed/unhealthy.
- **disk_temperature_celsius** → Current disk temperature (°C).
- **disk_power_on_hours** → Number of hours the disk has been powered on.
- **disk_reallocated_sectors** → Count of reallocated sectors (SMART attribute 5).
- **disk_media_errors** → Count of media errors detected by controller.
- **disk_predictive_failures** → `1` if disk is in predictive failure state, otherwise `0`.
- **disk_firmware_version** → Firmware version (as a label).
- **raid_disk_serial_number** → Serial number of the disk (as a label).

These metrics allow tracking the health of RAID disks and setting up alert rules in Prometheus.

---

## Exported Metrics (smartctl-based)

Each metric includes labels:
- `disk_id`
- `model`
- `serial`
- `device_name`

### Example Metrics

```text
# HELP current_pending_sectors Current pending sectors
# TYPE current_pending_sectors gauge
current_pending_sectors{disk_id="14",model="WDC WD4005FFBX-68CAUN0",mount_point="/dev/bus/1",serial="WD-BS01LX5H"} 0.0
current_pending_sectors{disk_id="15",model="WDC WD20EFZX-68AWUN0",mount_point="/dev/bus/1",serial="WD-WX62D71C524S"} 0.0
megacurrent_pending_sectors{disk_id="17",model="HGST HUS722T2TALA604",mount_point="/dev/bus/1",serial="WMC6N0L5DK62"} 1.0

# HELP offline_uncorrectable Offline uncorrectable sectors
# TYPE offline_uncorrectable gauge
offline_uncorrectable{disk_id="14",model="WDC WD4005FFBX-68CAUN0",mount_point="/dev/bus/1",serial="WD-BS01LX5H"} 0.0
offline_uncorrectable{disk_id="15",model="WDC WD20EFZX-68AWUN0",mount_point="/dev/bus/1",serial="WD-WX62D71C524S"} 0.0
offline_uncorrectable{disk_id="17",model="HGST HUS722T2TALA604",mount_point="/dev/bus/1",serial="WMC6N0L5DK62"} 0.0

# HELP power_on_hours Power on hours per disk
# TYPE power_on_hours gauge
power_on_hours{disk_id="14",model="WDC WD4005FFBX-68CAUN0",mount_point="/dev/bus/1",serial="WD-BS01LX5H"} 2329.0
power_on_hours{disk_id="15",model="WDC WD20EFZX-68AWUN0",mount_point="/dev/bus/1",serial="WD-WX62D71C524S"} 26098.0
power_on_hours{disk_id="17",model="HGST HUS722T2TALA604",mount_point="/dev/bus/1",serial="WMC6N0L5DK62"} 49707.0

# HELP reallocated_sectors Reallocated sectors count per disk
# TYPE reallocated_sectors gauge
reallocated_sectors{disk_id="14",model="WDC WD4005FFBX-68CAUN0",mount_point="/dev/bus/1",serial="WD-BS01LX5H"} 0.0
reallocated_sectors{disk_id="15",model="WDC WD20EFZX-68AWUN0",mount_point="/dev/bus/1",serial="WD-WX62D71C524S"} 0.0
reallocated_sectors{disk_id="17",model="HGST HUS722T2TALA604",mount_point="/dev/bus/1",serial="WMC6N0L5DK62"} 0.0

# HELP smart_passed SMART overall health passed status (1=PASSED, 0=FAILED)
# TYPE smart_passed gauge
smart_passed{device_name="/dev/bus/1",disk_id="14",model="WDC WD4005FFBX-68CAUN0",serial="WD-BS01LX5H"} 1.0
smart_passed{device_name="/dev/bus/1",disk_id="15",model="WDC WD20EFZX-68AWUN0",serial="WD-WX62D71C524S"} 1.0
smart_passed{device_name="/dev/bus/1",disk_id="17",model="HGST HUS722T2TALA604",serial="WMC6N0L5DK62"} 1.0

# HELP temperature_celsius Disk temperature in Celsius
# TYPE temperature_celsius gauge
temperature_celsius{disk_id="14",model="WDC WD4005FFBX-68CAUN0",mount_point="/dev/bus/1",serial="WD-BS01LX5H"} 28.0
temperature_celsius{disk_id="15",model="WDC WD20EFZX-68AWUN0",mount_point="/dev/bus/1",serial="WD-WX62D71C524S"} 27.0
temperature_celsius{disk_id="17",model="HGST HUS722T2TALA604",mount_point="/dev/bus/1",serial="WMC6N0L5DK62"} 30.0

# HELP udma_crc_errors UDMA CRC error count
# TYPE udma_crc_errors gauge
udma_crc_errors{disk_id="14",model="WDC WD4005FFBX-68CAUN0",mount_point="/dev/bus/1",serial="WD-BS01LX5H"} 0.0
udma_crc_errors{disk_id="15",model="WDC WD20EFZX-68AWUN0",mount_point="/dev/bus/1",serial="WD-WX62D71C524S"} 0.0
udma_crc_errors{disk_id="17",model="HGST HUS722T2TALA604",mount_point="/dev/bus/1",serial="WMC6N0L5DK62"} 0.0
```

These metrics allow real-time health inspection of disks behind MegaRAID with minimal overhead.

