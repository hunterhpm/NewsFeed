package newsFeed.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JOLTS extends Fetcher {

    private final JoltsType type;

    public JOLTS(JoltsType type) {
        this.type = type;
    }

    @Override
    public NewsEvent fetch() {

        String seriesId;

        if (type == JoltsType.JOB_OPENINGS) {
            seriesId = "JTS00000000JOL";
        } else {
            throw new UnsupportedOperationException(
                    "JOLTS type not implemented: " + type
            );
        }

        String json = fetchData(seriesId);

        try {
            ObjectMapper mapper = new ObjectMapper();

            JsonNode root = mapper.readTree(json);

            JsonNode data = root
                    .get("Results")
                    .get("series")
                    .get(0)
                    .get("data");

            JsonNode latest = data.get(0);

            String year = latest.get("year").asText();
            String month = latest.get("periodName").asText();

            double latestValue =
                    latest.get("value").asDouble();

            double previousValue =
                    data.get(1)
                            .get("value")
                            .asDouble();

            // BLS JOLTS level is reported in thousands.
            // Forex Factory displays it in millions.
            double actualMillions =
                    latestValue / 1000.0;

            double previousMillions =
                    previousValue / 1000.0;

            String actual =
                    String.format("%.2fM", actualMillions);

            String previous =
                    String.format("%.2fM", previousMillions);

            return new NewsEvent(
                    getLabel(),
                    actual,
                    previous,
                    "BLS",
                    year + " " + month
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getLabel() {
        return "JOLTS Job Openings";
    }
}