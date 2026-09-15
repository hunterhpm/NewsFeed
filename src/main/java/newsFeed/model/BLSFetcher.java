package newsFeed.model;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public abstract class BLSFetcher extends Fetcher {

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
}