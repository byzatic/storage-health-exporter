package io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector;

import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.smartctl.SmartCTLReader;
import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.model.MegaRAIDDiskInfo;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class RAIDMetricsCollectorWithCachingTest {

    private static final Logger logger = LoggerFactory.getLogger(RAIDMetricsCollectorWithCachingTest.class);

    @Test
    void unknownThenKnown_serial_resultsInSingleSeries() throws Exception {
        SmartCTLReader reader = Mockito.mock(SmartCTLReader.class);

        // 1) "unknown": empty serial/model -> key D:4
        MegaRAIDDiskInfo unk = new MegaRAIDDiskInfo();
        unk.diskId = 4;
        unk.model = "";
        unk.serial = "";
        unk.deviceName = "/dev/bus/0";
        unk.smartStatus = "PASSED";
        unk.currentPendingSectors = -1L;
        unk.reallocatedSectors = 0L;
        unk.offlineUncorrectable = 0L;
        unk.udmaCrcErrors = 0L;
        unk.temperatureCelsius = 0;
        unk.powerOnHours = 0;

        // 2) "known": real serial appears -> key S:Z9C0A00PFHRF
        MegaRAIDDiskInfo known = new MegaRAIDDiskInfo();
        known.diskId = 4;
        known.model = "LENOVO AL15SEB120N";
        known.serial = "Z9C0A00PFHRF";
        known.deviceName = "/dev/bus/0";
        known.smartStatus = "PASSED";
        known.currentPendingSectors = -1L;
        known.reallocatedSectors = 0L;
        known.offlineUncorrectable = 0L;
        known.udmaCrcErrors = 0L;
        known.temperatureCelsius = 0;
        known.powerOnHours = 0;

        logger.info("Preparing test disks: unknown(serial='{}'), known(serial='{}')", unk.serial, known.serial);

        when(reader.readDisks())
                .thenReturn(List.of(unk))
                .thenReturn(List.of(known));

        RAIDMetricsCollectorWithCaching collector = new RAIDMetricsCollectorWithCaching(reader);

        logger.info("Calling updateMetrics() #1 — expect a series with serial=\"unknown\"");
        collector.updateMetrics();

        logger.info("Calling updateMetrics() #2 — expect the series to switch to the real serial");
        collector.updateMetrics();

        int port = freePort();
        logger.info("Starting Prometheus HTTPServer on port {}", port);

        try (HTTPServer server = HTTPServer.builder().port(port).buildAndStart()) {
            String url = "http://127.0.0.1:" + port + "/metrics";
            String body = httpGet(url);
            logger.info("Fetched /metrics ({} bytes) from {}", body.length(), url);
            logger.debug("First lines of exposition:\n{}", firstLines(body, 25));

            Pattern series = Pattern.compile(
                    "^megaraid_current_pending_sectors\\{[^}]*disk_id=\"4\"[^}]*mount_point=\"/dev/bus/0\"[^}]*}\\s*(-?\\d+(?:\\.\\d+)?)$",
                    Pattern.MULTILINE
            );
            Matcher m = series.matcher(body);

            int count = 0;
            String matchedLine = null;
            while (m.find()) {
                count++;
                matchedLine = m.group(0);
                logger.debug("Match #{}: {}", count, matchedLine);
            }

            logger.info("Found {} matching series for megaraid_current_pending_sectors (disk_id=4, /dev/bus/0)", count);

            if (count != 1) {
                logger.warn("Expected exactly one series. Dumping full /metrics for troubleshooting:");
                logger.warn("\n{}", body);
            }

            assertEquals(1, count, "Exactly one series should remain for disk_id=4");
            assertNotNull(matchedLine);
            assertTrue(matchedLine.contains("serial=\"Z9C0A00PFHRF\""),
                    "Expected the remaining series to use the real serial");
            assertFalse(matchedLine.contains("serial=\"unknown\""),
                    "The 'serial=\"unknown\"' series should be removed");
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static String httpGet(String url) throws Exception {
        HttpClient hc = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString());
        logger.debug("HTTP {} → {}", url, resp.statusCode());
        assertEquals(200, resp.statusCode(), "GET /metrics must return 200");
        return resp.body();
    }

    private static String firstLines(String text, int maxLines) {
        String[] lines = text.split("\\R");
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(maxLines, lines.length);
        for (int i = 0; i < limit; i++) {
            sb.append(lines[i]).append('\n');
        }
        if (lines.length > limit) {
            sb.append("... (truncated) ...");
        }
        return sb.toString();
    }
}