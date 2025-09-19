package io.github.byzatic.utility.prometheus.exporter.storage_health_exporter;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class Configuration {
    private final static Logger logger = LoggerFactory.getLogger(App.class);
    public static final String APP_NAME = "storage-health-exporter";

    public static final String APP_VERSION = "1.1";

    // Scheduler cron syntax.
    //
    //Format: 5 or 6 space-separated fields:
    //  [seconds] minutes hours day-of-month month day-of-week
    //If only 5 fields are provided, the seconds field defaults to 0.
    //
    //Supported tokens per field:
    //  * (any), exact numbers (e.g., 5), ranges (a-b), lists (a,b,c),
    //  steps (*/n), and stepped ranges (a-b/n).
    //Names (JAN–DEC, SUN–SAT) and Quartz-specific tokens (?, L, W, #) are NOT supported.
    //Day-of-Month AND Day-of-Week must both match (AND semantics).
    //Day-of-Week uses 0–6, where 0 = Sunday.
    //
    //Examples:
    //  */10 * * * * *    → every 10 seconds
    //  0 */5 * * * *     → every 5 minutes (on second 0)
    //  0 15 10 * * *     → 10:15:00 every day
    //  0 0 12 * * 1-5    → 12:00:00 Monday–Friday (0=Sun…6=Sat)
    //
    //NOTE:
    //  The Quartz-style value "*/10 * * * * ?" is NOT valid here (the "?" token isn’t supported).
    //  Use the 6-field form "*/10 * * * * *" to run every 10 seconds.

    public static final String CRON_EXPRESSION_STRING;
    public static final URL PROMETHEUS_URL;

    public static final boolean FEATURE_FLAG_CACHING_COLLECTOR;

    static {
        try {
            logger.debug("Static block is executed.");

            Configurations configs = new Configurations();

            XMLConfiguration config = configs.xml("configuration/configuration.xml");

            CRON_EXPRESSION_STRING = config.getString("cronExpressionString");

            PROMETHEUS_URL = new URI(config.getString("prometheusEndpointURL")).toURL();

            FEATURE_FLAG_CACHING_COLLECTOR = config.getBoolean("featureFlagCachingCollector", false);
        } catch (MalformedURLException | URISyntaxException e) {
            logger.error("Exception : " + ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Error reading URL", e);
        } catch (ConfigurationException ce) {
            logger.error("Exception : " + ExceptionUtils.getStackTrace(ce));
            throw new RuntimeException("Error reading configuration", ce);
        }
    }
}
