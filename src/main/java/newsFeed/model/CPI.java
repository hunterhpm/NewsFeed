package newsFeed.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CPI extends Fetcher {

    private final CpiType type;

    public CPI(CpiType type) {
        this.type = type;
    }

    @Override
    public NewsEvent fetch() {

        String seriesId;

        if (type == CpiType.MOM) {
            seriesId = "CUSR0000SA0";
        } else if (type == CpiType.YOY) {
            seriesId = "CUUR0000SA0";
        } else if (type == CpiType.CORE_MOM) {
            seriesId = "CUSR0000SA0L1E";
        } else if (type == CpiType.CORE_YOY) {
            seriesId = "CUUR0000SA0L1E";
        } else {
            throw new UnsupportedOperationException(
                    "CPI type not implemented: " + type
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

            double latestValue =
                    latest.get("value").asDouble();

            double comparisonValue;

            if (type == CpiType.MOM
                    || type == CpiType.CORE_MOM) {

                comparisonValue =
                        data.get(1)
                                .get("value")
                                .asDouble();

            } else {

                int latestYear =
                        Integer.parseInt(year);

                comparisonValue =
                        findYearAgoValue(
                                data,
                                latestYear - 1,
                                latestPeriod
                        );
            }

            double result =
                    ((latestValue / comparisonValue) - 1) * 100;

            String formattedResult =
                    String.format("%.1f", result);

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
                "Could not find year-ago CPI value."
        );
    }

    @Override
    protected String getLabel() {

        if (type == CpiType.MOM) {
            return "CPI m/m";
        } else if (type == CpiType.YOY) {
            return "CPI y/y";
        } else if (type == CpiType.CORE_MOM) {
            return "Core CPI m/m";
        } else {
            return "Core CPI y/y";
        }
    }
}