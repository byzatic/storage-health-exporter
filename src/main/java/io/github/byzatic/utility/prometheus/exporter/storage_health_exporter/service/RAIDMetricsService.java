package io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.service;

import io.github.byzatic.commons.schedulers.cron.CronScheduler;
import io.github.byzatic.commons.schedulers.cron.CronSchedulerInterface;
import io.github.byzatic.commons.schedulers.cron.CronTask;
import io.github.byzatic.commons.schedulers.cron.JobEventListener;
import io.github.byzatic.utility.prometheus.exporter.storage_health_exporter.collector.RAIDMetricsCollectorInterface;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class RAIDMetricsService implements RAIDMetricsServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(RAIDMetricsService.class);

    private final int port;
    private final String address;
    private final String cronExpressionString;
    private final RAIDMetricsCollectorInterface collector;

    private final CountDownLatch stopLatch = new CountDownLatch(1);
    private final AtomicReference<Throwable> fatalError = new AtomicReference<>(null);

    private volatile boolean stopping = false;
    private volatile CronSchedulerInterface scheduler;
    private volatile UUID jobId;

    public RAIDMetricsService(URL prometheusEndpointURL,
                              String cronExpressionString,
                              RAIDMetricsCollectorInterface collector) {
        this.port = prometheusEndpointURL.getPort();
        this.address = prometheusEndpointURL.getHost();
        this.cronExpressionString = cronExpressionString;
        this.collector = collector;
    }

    @Override
    public void run() throws IOException {
        // HTTPServer автоматически закрывается по выходу из try-with-resources
        try (HTTPServer ignored = HTTPServer.builder().hostname(address).port(port).buildAndStart();
             CronScheduler cron = new CronScheduler.Builder().build()) {

            logger.debug("HTTPServer listening on http://{}:{}{}", address, port, "/metrics");
            logger.warn("HTTPServer using default location {}", "/metrics");

            this.scheduler = cron;

            // Подпишемся на события заданий
            cron.addListener(new JobEventListener() {
                @Override
                public void onStart(UUID jobId) {
                    logger.debug("Metrics job {} started", jobId);
                }

                @Override
                public void onComplete(UUID jobId) {
                    logger.debug("Metrics job {} completed", jobId);
                }

                @Override
                public void onError(UUID jobId, Throwable error) {
                    logger.error("Metrics job {} failed", jobId, error);
                    fatalError.compareAndSet(null, error);
                    stopLatch.countDown(); // выходим из run()
                }

                @Override
                public void onTimeout(UUID jobId) {
                    logger.error("Metrics job {} timed out", jobId);
                    fatalError.compareAndSet(null, new RuntimeException("Metrics job timed out"));
                    stopLatch.countDown();
                }

                @Override
                public void onCancelled(UUID jobId) {
                    logger.info("Metrics job {} cancelled", jobId);
                    // Если это пользовательская остановка — просто выходим.
                    if (stopping) {
                        stopLatch.countDown();
                    }
                }
            });

            // Само задание обновления метрик
            CronTask updateTask = token -> {
                // Если поступил запрос на остановку — прерываемся до выполнения тяжелой части
                if (token.isStopRequested()) {
                    token.throwIfStopRequested();
                }
                try {
                    collector.updateMetrics();
                } catch (Exception e) {
                    // Пусть планировщик зафиксирует ошибку и вызовет onError
                    throw e;
                }
                // Еще раз проверим флаг остановки
                token.throwIfStopRequested();
            };

            // Регистрируем задание:
            // - запрещаем overlap (disallowOverlap = true)
            // - запускаем немедленно (runImmediately = true), затем по cron
            this.jobId = cron.addJob(cronExpressionString, updateTask, true, true);
            logger.info("Metrics job {} scheduled with cron '{}'", jobId, cronExpressionString);

            // Блокируем поток сервиса до остановки/ошибки
            stopLatch.await();

            // Если упали по ошибке — пробросим исключение
            Throwable t = fatalError.get();
            if (t != null) {
                throw new RuntimeException("Update metrics process finished with error", t);
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Service interrupted", ie);
        } finally {
            // Гарантируем закрытие планировщика при любых сценариях
            CronSchedulerInterface s = this.scheduler;
            if (s != null) {
                try {
                    s.close();
                } catch (Exception e) {
                    logger.warn("Error while closing scheduler", e);
                }
            }
        }
    }

    @Override
    public void terminate() {
        stopping = true;
        CronSchedulerInterface s = this.scheduler;
        UUID id = this.jobId;

        // Мягкая остановка текущего запуска (если идет), даем 5 секунд на корректное завершение
        if (s != null && id != null) {
            try {
                s.stopJob(id, Duration.ofSeconds(5));
            } catch (Exception e) {
                logger.warn("Error while requesting job stop", e);
            }
        }

        // Снимаем блокировку run()
        stopLatch.countDown();
    }
}