package newsFeed.model.fetcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public abstract class BEAFetcher extends Fetcher {

    private static final String BEA_API_KEY =
            System.getenv("BEA_API_KEY");

    protected String fetchData(
            String tableName,
            String frequency) {

        if (BEA_API_KEY == null || BEA_API_KEY.isBlank()) {
            throw new IllegalStateException(
                    "BEA_API_KEY environment variable is not set."
            );
        }

        String url =
                "https://apps.bea.gov/api/data/"
                        + "?UserID=" + BEA_API_KEY
                        + "&method=GetData"
                        + "&DataSetName=NIPA"
                        + "&TableName=" + tableName
                        + "&Frequency=" + frequency
                        + "&Year=ALL"
                        + "&ResultFormat=JSON";

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            return response.body();

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected String fetchPage(String url) {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "NewsFeed/1.0")
                .GET()
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "BEA returned HTTP " + response.statusCode()
                );
            }

            return response.body();

        } catch (IOException e) {
            throw new RuntimeException(e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}