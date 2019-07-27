package org.test;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullWriter;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    private static final int THREADS = 150;

    private static final long DURATION_MS = TimeUnit.SECONDS.toMillis(60);

    private static final int TIMEOUT_MS = 100;

    private final Queue<RequestResult> queue;

    private final List<String> urls;

    private Application() throws Exception {
        this.queue = new ConcurrentLinkedDeque<>();
        this.urls = Utility.loadUrls("lga-doubleverify03.pulse.prod");
    }

    public static void main(String[] arguments) throws Exception {
        Application application = new Application();
        application.run();
    }

    private void run() throws Exception {
        LOGGER.info("Application started with\nthreads={}\nduration={}ms\ntimeout={}ms\nurls={}",
                THREADS, DURATION_MS, TIMEOUT_MS, urls.size());

        long tickNs = System.nanoTime();

        Collection<Thread> threads = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            threads.add(new Worker());
        }

        LOGGER.info("Starting threads");
        for (Thread thread : threads) {
            thread.start();
        }

        LOGGER.info("Waiting operations...");
        Thread.sleep(DURATION_MS);

        LOGGER.info("Interrupting threads");
        for (Thread thread : threads) {
            thread.interrupt();
        }

        LOGGER.info("Waiting all threads to close...");
        for (Thread thread : threads) {
            thread.join();
        }

        long elapsedNs = System.nanoTime() - tickNs;

        System.out.printf("elapsed %dns%n", elapsedNs);
        System.out.printf("elapsed %.3fsec%n", elapsedNs / 1_000_000_000.0);

        List<RequestResult> list = new ArrayList<>(queue);
        list.sort(Comparator.comparingLong(r -> r.elapsedNs));

        System.out.printf("Total %d%n", list.size());
        System.out.printf("200 %d%n", list.stream().filter(r -> r.code == 200).count());
        System.out.printf("QPS %.1f/sec%n", list.size() / (elapsedNs / 1_000_000_000.0));

        System.out.printf("p50 %.1fms%n", list.get(Math.round(list.size() * 0.50f)).elapsedNs / 1_000_000.0);
        System.out.printf("p75 %.1fms%n", list.get(Math.round(list.size() * 0.75f)).elapsedNs / 1_000_000.0);
        System.out.printf("p90 %.1fms%n", list.get(Math.round(list.size() * 0.90f)).elapsedNs / 1_000_000.0);
        System.out.printf("p95 %.1fms%n", list.get(Math.round(list.size() * 0.95f)).elapsedNs / 1_000_000.0);
        System.out.printf("p99 %.1fms%n", list.get(Math.round(list.size() * 0.99f)).elapsedNs / 1_000_000.0);
        System.out.printf("max %.1fms%n", list.get(list.size() -1).elapsedNs / 1_000_000.0);

        Map<Integer, Long> codes = list.stream()
                .collect(Collectors.groupingBy(r -> r.code, Collectors.counting()));
        System.out.printf("codes: %s%n", codes);

        Map<String, Long> exceptions = list.stream()
                .collect(Collectors.groupingBy(r -> r.exception, Collectors.counting()));
        System.out.printf("exceptions: %s%n", exceptions);
    }

    private class Worker extends Thread {

        private HttpClient client;

        public Worker() {
            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
            connectionManager.setMaxTotal(1);
            connectionManager.setDefaultMaxPerRoute(1);

            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(TIMEOUT_MS)
                    .setConnectionRequestTimeout(TIMEOUT_MS)
                    .setSocketTimeout(TIMEOUT_MS)
                    .setCircularRedirectsAllowed(false)
                    .setRedirectsEnabled(false)
                    .setRelativeRedirectsAllowed(false)
                    .setMaxRedirects(0)
                    .setContentCompressionEnabled(false)
                    .setAuthenticationEnabled(false)
                    .build();

            DefaultHttpRequestRetryHandler retryHandler =
                    new DefaultHttpRequestRetryHandler(0, false);

            DefaultConnectionKeepAliveStrategy keepAliveStrategy =
                    new DefaultConnectionKeepAliveStrategy();

            client = HttpClientBuilder.create()
                    .setMaxConnTotal(1)
                    .setConnectionManager(connectionManager)
                    .setKeepAliveStrategy(keepAliveStrategy)
                    .setRetryHandler(retryHandler)
                    .setDefaultRequestConfig(requestConfig)
                    .disableCookieManagement()
                    .disableAutomaticRetries()
                    .disableContentCompression()
                    .disableRedirectHandling()
                    .build();
        }

        @Override
        public void run() {
            try {
                while (!interrupted()) {
                    long tickNs = System.nanoTime();

                    String url = urls.get(ThreadLocalRandom.current().nextInt(urls.size()));

                    HttpGet request = new HttpGet(url);

                    try {
                        HttpResponse response = client.execute(request);

                        int status = response.getStatusLine().getStatusCode();

                        HttpEntity entity = response.getEntity();

                        // load all content to /dev/null
                        try (InputStream is = entity.getContent()) {
                            IOUtils.copy(is, NullWriter.NULL_WRITER, StandardCharsets.UTF_8);
                        }

                        RequestResult r = new RequestResult();
                        r.code = status;
                        r.elapsedNs = System.nanoTime() - tickNs;
                        r.exception = "ok";

                        queue.add(r);
                    } catch (Exception e) {
                        RequestResult r = new RequestResult();
                        r.code = -1;
                        r.elapsedNs = System.nanoTime() - tickNs;
                        r.exception = e.getClass().getCanonicalName();

                        queue.add(r);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Unexpected error", e);
            }
        }
    }

    private static final class RequestResult implements Serializable {

        private int code;

        private long elapsedNs;

        private String exception;

    }
}
