package newsFeed.model;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public abstract class Fetcher {

    private static final String BLS_API_KEY =
            System.getenv("BLS_API_KEY");

    protected String fetchData(String seriesId) {

        if (BLS_API_KEY == null || BLS_API_KEY.isBlank()) {
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
                """.formatted(seriesId, BLS_API_KEY);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://api.bls.gov/publicAPI/v2/timeseries/data/"
                ))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            return response.body();

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void watchForNewEvents() {

        NewsEvent current = fetch();

        printEvent(current);

        String lastPeriod = current.period();

        int dotCount = 1;
        int secondsSinceCheck = 0;

        while (true) {

            String dots = ".".repeat(dotCount);

            System.out.print(
                    "\rWaiting for new event" + dots + "   "
            );

            dotCount++;

            if (dotCount > 3) {
                dotCount = 1;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            secondsSinceCheck++;

            if (secondsSinceCheck >= 60) {

                secondsSinceCheck = 0;

                NewsEvent newest = fetch();

                if (!newest.period().equals(lastPeriod)) {

                    System.out.println();
                    System.out.println();

                    printEvent(newest);

                    lastPeriod = newest.period();
                }
            }
        }
    }

    protected void printEvent(NewsEvent event) {

        System.out.println(
                event.period() + " "
                        + event.name() + ": "
                        + event.actual()
        );
    }

    public abstract NewsEvent fetch();

    protected abstract String getLabel();
}