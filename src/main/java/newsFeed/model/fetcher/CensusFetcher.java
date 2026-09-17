package newsFeed.model.fetcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public abstract class CensusFetcher extends Fetcher {

    private static final String CENSUS_API_KEY =
            System.getenv("CENSUS_API_KEY");

    protected String fetchData(
            String program,
            String categoryCode,
            String dataTypeCode) {

        if (CENSUS_API_KEY == null || CENSUS_API_KEY.isBlank()) {
            throw new IllegalStateException(
                    "CENSUS_API_KEY environment variable is not set."
            );
        }

        String url =
                "https://api.census.gov/data/timeseries/eits/"
                        + program
                        + "?get=cell_value,time_slot_id"
                        + "&category_code=" + categoryCode
                        + "&data_type_code=" + dataTypeCode
                        + "&seasonally_adj=yes"
                        + "&for=us:*"
                        + "&time=2026"
                        + "&key=" + CENSUS_API_KEY;

        HttpRequest request = HttpRequest.newBuilder()
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
}