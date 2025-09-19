package io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.exceptions;

public class CollectorException extends Exception {
    public CollectorException(String message) {
        super(message);
    }

    public CollectorException(String message, Throwable cause) {
        super(message, cause);
    }

    public CollectorException(Exception e) {
        super(e);
    }
}
