package newsFeed.model.fetcher;

import newsFeed.model.source.NewsEvent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public abstract class Fetcher {

    private static final String BLS_API_KEY =
            System.getenv("BLS_API_KEY");

    private static final long RSS_WATCH_INTERVAL_MILLIS =
            1000;

    private static final ExecutorService BENCHMARK_EXECUTOR =
            Executors.newFixedThreadPool(2, r -> {
                Thread thread =
                        new Thread(
                                r,
                                "news-feed-benchmark"
                        );

                thread.setDaemon(true);

                return thread;
            });

    protected String fetchData(String seriesId) {

        if (BLS_API_KEY == null
                || BLS_API_KEY.isBlank()) {

            throw new IllegalStateException(
                    "BLS_API_KEY environment variable is not set."
            );
        }

        String jsonBody =
                """
                {
                    "seriesid": ["%s"],
                    "calculations": true,
                    "registrationkey": "%s"
                }
                """.formatted(
                        seriesId,
                        BLS_API_KEY
                );

        HttpClient client =
                HttpClient.newHttpClient();

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        "https://api.bls.gov/publicAPI/v2/timeseries/data/"
                                )
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(jsonBody)
                        )
                        .build();

        try {

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            return response.body();

        } catch (IOException
                 | InterruptedException e) {

            throw new RuntimeException(e);
        }
    }

    /**
     * Fetches the event using its normal source and,
     * when an official RSS feed is configured,
     * fetches the RSS feed at the same time.
     *
     * This compares request/processing latency for
     * already-published data.
     *
     * It does not prove which source publishes a new
     * release first. Use watchForNewEventsWithRSSBenchmark()
     * around an actual release for that.
     */
    public NewsEvent fetchWithRSSBenchmark() {

        Optional<RSSFeedConfig> rssFeed =
                RSSFeedRegistry.forFetcher(this);

        if (rssFeed.isEmpty()) {
            return fetch();
        }

        Comparison comparison =
                compare(rssFeed.get());

        printComparison(comparison);

        return comparison.current()
                .value();
    }

    /**
     * Watches the normal source and RSS feed side-by-side.
     *
     * When both channels have detected the new release,
     * their detection times are compared and browser
     * resources are closed.
     *
     * If the event has no verified RSS feed, only the
     * normal source is watched.
     */
    public void watchForNewEventsWithRSSBenchmark() {

        Optional<RSSFeedConfig> rssFeedOptional =
                RSSFeedRegistry.forFetcher(this);

        if (rssFeedOptional.isEmpty()) {

            watchCurrentSourceOnly();

            return;
        }

        RSSFeedConfig rssFeed =
                rssFeedOptional.get();

        System.out.println(
                "Warming current source and RSS..."
        );

        Comparison baseline =
                compare(rssFeed);

        printComparison(baseline);

        String lastPeriod =
                baseline.current()
                        .value()
                        .period();

        String lastRssIdentity =
                baseline.rss()
                        .value()
                        .snapshot()
                        .identity();

        Instant currentDetectedAt =
                null;

        Instant rssDetectedAt =
                null;

        System.out.printf(
                Locale.ROOT,
                "Watching %s every %.1f second(s).%n",
                getLabel(),
                RSS_WATCH_INTERVAL_MILLIS
                        / 1000.0
        );

        while (!Thread.currentThread()
                .isInterrupted()) {

            try {

                Thread.sleep(
                        RSS_WATCH_INTERVAL_MILLIS
                );

            } catch (InterruptedException e) {

                Thread.currentThread()
                        .interrupt();

                closeResources();

                return;
            }

            Comparison comparison;

            try {

                comparison =
                        compare(rssFeed);

            } catch (RuntimeException e) {

                System.out.println();

                System.out.println(
                        "Benchmark check failed: "
                                + e.getMessage()
                );

                continue;
            }

            NewsEvent currentEvent =
                    comparison.current()
                            .value();

            RSSSnapshot rssSnapshot =
                    comparison.rss()
                            .value()
                            .snapshot();

            /*
             * Normal/current source detected
             * the new release.
             */
            if (currentDetectedAt == null
                    && !currentEvent.period()
                    .equals(lastPeriod)) {

                currentDetectedAt =
                        comparison.current()
                                .completedAt();

                System.out.printf(
                        Locale.ROOT,
                        "%nCURRENT SOURCE detected new release "
                                + "at %s "
                                + "(request %.3f s): %s %s%n",
                        currentDetectedAt,
                        comparison.current()
                                .elapsedSeconds(),
                        currentEvent.period(),
                        currentEvent.actual()
                );
            }

            /*
             * RSS detected the new release.
             */
            if (rssDetectedAt == null
                    && !rssSnapshot.identity()
                    .equals(lastRssIdentity)) {

                rssDetectedAt =
                        comparison.rss()
                                .completedAt();

                System.out.printf(
                        Locale.ROOT,
                        "%nRSS detected new release at %s "
                                + "(request %.3f s): %s%n",
                        rssDetectedAt,
                        comparison.rss()
                                .elapsedSeconds(),
                        rssSnapshot.title()
                );
            }

            /*
             * Wait until both have detected the release
             * so we can make a fair comparison.
             */
            if (currentDetectedAt != null
                    && rssDetectedAt != null) {

                long differenceMillis =
                        Math.abs(
                                currentDetectedAt
                                        .toEpochMilli()
                                        - rssDetectedAt
                                        .toEpochMilli()
                        );

                System.out.println();

                if (currentDetectedAt
                        .isBefore(rssDetectedAt)) {

                    System.out.printf(
                            Locale.ROOT,
                            "RESULT: current source appeared "
                                    + "first by about "
                                    + "%.3f seconds.%n",
                            differenceMillis
                                    / 1000.0
                    );

                } else if (rssDetectedAt
                        .isBefore(currentDetectedAt)) {

                    System.out.printf(
                            Locale.ROOT,
                            "RESULT: RSS appeared first "
                                    + "by about "
                                    + "%.3f seconds.%n",
                            differenceMillis
                                    / 1000.0
                    );

                } else {

                    System.out.println(
                            "RESULT: both were detected "
                                    + "at the same millisecond."
                    );
                }

                /*
                 * Target release has been detected
                 * and comparison is finished.
                 */
                closeResources();

                return;
            }
        }

        closeResources();
    }

    /**
     * Used when an event does not have a verified
     * official RSS feed.
     *
     * The browser/session remains alive while polling
     * and closes only after a new release is detected.
     */
    private void watchCurrentSourceOnly() {

        NewsEvent baseline = fetch();

        String lastPeriod =
                baseline.period();

        while (!Thread.currentThread().isInterrupted()) {

            try {
                Thread.sleep(
                        RSS_WATCH_INTERVAL_MILLIS
                );

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                closeResources();
                return;
            }

            NewsEvent current;

            try {
                current = fetch();

            } catch (RuntimeException e) {

                System.out.println(
                        "Check failed: "
                                + e.getMessage()
                );

                continue;
            }

            if (!current.period()
                    .equals(lastPeriod)) {

                printEvent(current);

                closeResources();
                return;
            }
        }

        closeResources();
    }

    private Comparison compare(
            RSSFeedConfig rssFeed) {

        CompletableFuture<TimedResult<NewsEvent>>
                currentFuture =
                CompletableFuture.supplyAsync(
                        () -> timed(this::fetch),
                        BENCHMARK_EXECUTOR
                );

        CompletableFuture<TimedResult<RSSFetchResult>>
                rssFuture =
                CompletableFuture.supplyAsync(
                        () -> timed(
                                () -> RSSFeedReader
                                        .read(rssFeed)
                        ),
                        BENCHMARK_EXECUTOR
                );

        return new Comparison(
                currentFuture.join(),
                rssFuture.join()
        );
    }

    private void printComparison(
            Comparison comparison) {

        NewsEvent current =
                comparison.current()
                        .value();

        RSSFetchResult rss =
                comparison.rss()
                        .value();

        System.out.println();

        System.out.println(
                "=== Current source vs RSS ==="
        );

        System.out.printf(
                Locale.ROOT,
                "Current method: %.3f seconds -> "
                        + "%s %s = %s%n",
                comparison.current()
                        .elapsedSeconds(),
                current.period(),
                current.name(),
                current.actual()
        );

        System.out.printf(
                Locale.ROOT,
                "RSS request:    %.3f seconds -> %s%n",
                comparison.rss()
                        .elapsedSeconds(),
                rss.snapshot()
                        .title()
        );

        if (!rss.snapshot()
                .published()
                .isBlank()) {

            System.out.println(
                    "RSS published:  "
                            + rss.snapshot()
                            .published()
            );
        }

        double difference =
                Math.abs(
                        comparison.current()
                                .elapsedSeconds()
                                - comparison.rss()
                                .elapsedSeconds()
                );

        if (comparison.current()
                .elapsedNanos()
                < comparison.rss()
                .elapsedNanos()) {

            System.out.printf(
                    Locale.ROOT,
                    "Faster request: current method "
                            + "by %.3f seconds%n",
                    difference
            );

        } else if (comparison.rss()
                .elapsedNanos()
                < comparison.current()
                .elapsedNanos()) {

            System.out.printf(
                    Locale.ROOT,
                    "Faster request: RSS "
                            + "by %.3f seconds%n",
                    difference
            );

        } else {

            System.out.println(
                    "Faster request: tie"
            );
        }

        System.out.println(
                "Note: current-data latency is not "
                        + "the same as release-publication timing."
        );
    }

    private static <T> TimedResult<T> timed(
            Supplier<T> supplier) {

        long start =
                System.nanoTime();

        T value =
                supplier.get();

        long end =
                System.nanoTime();

        return new TimedResult<>(
                value,
                end - start,
                Instant.now()
        );
    }

    public void watchForNewEvents() {

        NewsEvent current =
                fetch();

        printEvent(current);

        String lastPeriod =
                current.period();

        int dotCount =
                1;

        int secondsSinceCheck =
                0;

        while (true) {

            String dots =
                    ".".repeat(dotCount);

            System.out.print(
                    "\rWaiting for new event"
                            + dots
                            + "   "
            );

            dotCount++;

            if (dotCount > 3) {
                dotCount = 1;
            }

            try {

                Thread.sleep(1000);

            } catch (InterruptedException e) {

                Thread.currentThread()
                        .interrupt();

                closeResources();

                return;
            }

            secondsSinceCheck++;

            if (secondsSinceCheck >= 60) {

                secondsSinceCheck =
                        0;

                NewsEvent newest =
                        fetch();

                if (!newest.period()
                        .equals(lastPeriod)) {

                    System.out.println();
                    System.out.println();

                    printEvent(newest);

                    lastPeriod =
                            newest.period();
                }
            }
        }
    }

    protected void printEvent(
            NewsEvent event) {

        System.out.println(
                event.period()
                        + " "
                        + event.name()
                        + ": "
                        + event.actual()
        );
    }

    public abstract NewsEvent fetch();

    protected abstract String getLabel();

    protected static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder()
                    .followRedirects(
                            HttpClient.Redirect.NORMAL
                    )
                    .build();

    private record TimedResult<T>(
            T value,
            long elapsedNanos,
            Instant completedAt
    ) {

        double elapsedSeconds() {

            return elapsedNanos
                    / 1_000_000_000.0;
        }
    }

    private record Comparison(
            TimedResult<NewsEvent> current,
            TimedResult<RSSFetchResult> rss
    ) {
    }

    /**
     * Fetchers that own browser resources override this.
     *
     * Normal HTTP/JSON/XML fetchers have nothing to close.
     */
    protected void closeResources() {
        // Nothing to close by default.
    }
}