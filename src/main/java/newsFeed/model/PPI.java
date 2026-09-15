package newsFeed.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PPI extends Fetcher {

    private final PPIType type;

    public PPI(PPIType type) {
        this.type = type;
    }

    @Override
    public NewsEvent fetch() {

        String seriesId;

        if (type == PPIType.MOM) {
            seriesId = "WPSFD4";
        } else if (type == PPIType.YOY) {
            seriesId = "WPUFD4";
        } else if (type == PPIType.CORE_MOM) {
            seriesId = "WPSFD49104";
        } else if (type == PPIType.CORE_YOY) {
            seriesId = "WPUFD49104";
        } else {
            throw new UnsupportedOperationException(
                    "PPI type not implemented: " + type
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
            String latestPeriod = latest.get("period").asText();

            double latestValue = latest.get("value").asDouble();

            double comparisonValue;

            if (type == PPIType.MOM || type == PPIType.CORE_MOM) {

                comparisonValue =
                        data.get(1)
                                .get("value")
                                .asDouble();

            } else {

                int latestYear = Integer.parseInt(year);

                comparisonValue =
                        findYearAgoValue(
                                data,
                                latestYear - 1,
                                latestPeriod
                        );
            }

            double result = ((latestValue / comparisonValue) - 1) * 100;

            String formattedResult = String.format("%.1f", result);

            return new NewsEvent(
                    getLabel(),
                    formattedResult + "%",
                    "",
                    "BLS",
                    year + " " + month
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private double findYearAgoValue(
            JsonNode data,
            int targetYear,
            String targetPeriod) {

        for (JsonNode item : data) {

            String year =
                    item.get("year").asText();

            String period =
                    item.get("period").asText();

            if (year.equals(String.valueOf(targetYear))
                    && period.equals(targetPeriod)) {

                return item
                        .get("value")
                        .asDouble();
            }
        }

        throw new IllegalStateException(
                "Could not find year-ago PPI value."
        );
    }

    @Override
    protected String getLabel() {

        if (type == PPIType.MOM) {
            return "PPI m/m";
        } else if (type == PPIType.YOY) {
            return "PPI y/y";
        } else if (type == PPIType.CORE_MOM) {
            return "Core PPI m/m";
        } else {
            return "Core PPI y/y";
        }
    }
}