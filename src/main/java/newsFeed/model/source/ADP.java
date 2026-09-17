package newsFeed.model.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import newsFeed.model.fetcher.ADPFetcher;
import newsFeed.model.type.ADPType;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.Instant;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;

public class ADP extends ADPFetcher {

    private static final String JSON_URL =
            "https://adpemploymentreport.com/ner_production.json";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter PERIOD_FORMAT =
            DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH);

    private final ADPType type;

    public ADP(ADPType type) {
        this.type = type;
    }

    @Override
    public NewsEvent fetch() {

        if (type != ADPType.NON_FARM_EMPLOYMENT_CHANGE) {
            throw new UnsupportedOperationException(
                    "ADP type not implemented: " + type
            );
        }

        FetchResult result = measure("JSON", () -> parseJson(fetchData(JSON_URL)));
        if (result.error() != null) throw result.error();
        return result.event();
    }

    @Override
    public void watchForNewEvents() {
        watchForNewEvents(ADPReleaseMonitor.Settings.fromProperties());
    }

    void watchForNewEvents(ADPReleaseMonitor.Settings settings) {
        if (type != ADPType.NON_FARM_EMPLOYMENT_CHANGE) {
            throw new UnsupportedOperationException("ADP type not implemented: " + type);
        }
        var monitor = new ADPReleaseMonitor(System.out::println);
        System.out.println(settings.releaseAt() == null
                ? "ADP watching JSON every 60 seconds. Set adp.releaseAt (UTC timestamp) to enable the release window."
                : "ADP release window: " + settings.releaseAt().minus(settings.lead()) + " to "
                        + settings.releaseAt().plus(settings.window()) + "; polling JSON every "
                        + settings.fast().toMillis() + " ms. Start before release to establish the baseline.");
        poll("JSON", () -> parseJson(fetchData(JSON_URL)), monitor, settings);
    }

    private void poll(String source, Supplier<NewsEvent> fetch, ADPReleaseMonitor monitor,
                      ADPReleaseMonitor.Settings settings) {
        int failures = 0;
        while (!Thread.currentThread().isInterrupted()) {
            Instant started = Instant.now();
            FetchResult result = measure(source, fetch, monitor);
            if (Thread.currentThread().isInterrupted()) return;
            Duration wait = settings.untilNext(started, Instant.now(), monitor.needsFastPolling());
            if (result.error() != null) {
                // Back off on failure and honor the server's requested retry delay.
                failures = Math.min(failures + 1, 6);
                Duration backoff = Duration.ofSeconds(Math.min(60, 1L << failures));
                if (result.error() instanceof ADPRequestException request && request.retryAfter().compareTo(backoff) > 0) {
                    backoff = request.retryAfter();
                }
                if (backoff.compareTo(wait) > 0) wait = backoff;
            } else {
                failures = 0;
            }
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private FetchResult measure(String source, Supplier<NewsEvent> fetch) {
        return measure(source, fetch, null);
    }

    private FetchResult measure(String source, Supplier<NewsEvent> fetch, ADPReleaseMonitor monitor) {
        long start = System.nanoTime();
        NewsEvent event = null;
        RuntimeException error = null;
        try {
            event = fetch.get();
        } catch (RuntimeException e) {
            error = e;
        }
        long completed = System.nanoTime();
        Instant detectedAt = Instant.now();
        double seconds = (completed - start) / 1_000_000_000.0;
        if (monitor != null && event != null) {
            monitor.accept(event, detectedAt);
        }
        String outcome = event != null
                ? event.period() + " | " + event.actual()
                : "FAILED | " + error.getMessage();
        // One print per completion also keeps the watcher's waiting dots off this line.
        System.out.printf(Locale.ROOT, "%nADP %s: %.6f seconds (fetch + parse) | %s%n",
                source, seconds, outcome);
        return new FetchResult(event, error);
    }

    private record FetchResult(NewsEvent event, RuntimeException error) { }

    NewsEvent parseJson(String body) {
        try {
            JsonNode root = JSON.readTree(body);
            if (root == null || !"NER".equals(root.path("reportType").asText())) {
                throw new IllegalStateException("Expected an ADP National Employment Report.");
            }
            String period = YearMonth.parse(requiredText(root, "reportMonth") + " "
                    + requiredText(root, "reportYear"), PERIOD_FORMAT).format(PERIOD_FORMAT);
            JsonNode cards = root.path("reportOverview").path("cards");
            if (!cards.isArray()) {
                throw new IllegalStateException("Missing ADP report overview cards.");
            }
            JsonNode employment = null;
            for (JsonNode card : cards) {
                if ("Employment Change".equals(card.path("metricName").asText())) {
                    if (employment != null) {
                        throw new IllegalStateException("Multiple ADP headline employment metrics.");
                    }
                    employment = card;
                }
            }
            if (employment == null) {
                throw new IllegalStateException("Missing ADP headline employment metric.");
            }
            String value = requiredText(employment, "metricValue")
                    .replace(",", "").replace('−', '-');
            if (!value.matches("[+-]?(?:[0-9]+)")) {
                throw new IllegalStateException("Invalid ADP employment value: " + value);
            }
            long change = Long.parseLong(value);
            String direction = employment.path("metricDirection").asText().trim();
            if ("down".equalsIgnoreCase(direction)) {
                change = -Math.abs(change);
            } else if (change < 0 && "up".equalsIgnoreCase(direction)) {
                throw new IllegalStateException("ADP value and direction disagree.");
            } else if (change > 0 && !value.startsWith("+")
                    && !"up".equalsIgnoreCase(direction)) {
                throw new IllegalStateException("Missing or unknown ADP employment direction.");
            }
            return event(period, change);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid ADP JSON response.", e);
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("Missing ADP field: " + field);
        }
        return value;
    }

    private NewsEvent event(String period, long change) {
        period = YearMonth.parse(period, PERIOD_FORMAT).format(PERIOD_FORMAT);
        String actual = BigDecimal.valueOf(change).movePointLeft(3)
                .stripTrailingZeros().toPlainString() + "K";
        return new NewsEvent(getLabel(), actual, "", "ADP", period);
    }

    @Override
    protected String getLabel() {
        return "ADP Non-Farm Employment Change";
    }
}