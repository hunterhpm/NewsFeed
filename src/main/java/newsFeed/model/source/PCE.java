package newsFeed.model.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import newsFeed.model.fetcher.BEAFetcher;
import newsFeed.model.type.PCEType;

public class PCE extends BEAFetcher {

    private final PCEType type;

    public PCE(PCEType type) {
        this.type = type;
    }

    @Override
    public NewsEvent fetch() {

        if (type != PCEType.CORE_MOM) {
            throw new UnsupportedOperationException(
                    "PCE type not implemented: " + type
            );
        }

        String json = fetchData("T20807", "M");

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode results = root
                    .get("BEAAPI")
                    .get("Results");

            JsonNode data = results.get("Data");

            if (data == null) {
                throw new IllegalStateException(
                        "BEA returned no PCE data: " + results
                );
            }

            JsonNode latest = null;
            String latestPeriod = "";

            for (JsonNode item : data) {

                String lineNumber = item
                        .get("LineNumber")
                        .asText();

                if (!lineNumber.equals("6")) {
                    continue;
                }

                String period = item
                        .get("TimePeriod")
                        .asText();

                if (latest == null
                        || period.compareTo(latestPeriod) > 0) {

                    latest = item;
                    latestPeriod = period;
                }
            }

            if (latest == null) {
                throw new IllegalStateException(
                        "Could not find Core PCE data."
                );
            }

            double value = Double.parseDouble(
                    latest.get("DataValue")
                            .asText()
                            .replace(",", "")
            );

            String actual = String.format("%.1f%%", value);

            return new NewsEvent(
                    getLabel(),
                    actual,
                    "",
                    "BEA",
                    latestPeriod
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getLabel() {
        return "Core PCE Price Index m/m";
    }
}