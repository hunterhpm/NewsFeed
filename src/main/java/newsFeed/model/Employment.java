package newsFeed.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Employment extends Fetcher {

    private final EmploymentType type;

    public Employment(EmploymentType type) {
        this.type = type;
    }

    @Override
    public NewsEvent fetch() {

        String seriesId;

        if (type == EmploymentType.NFP) {
            seriesId = "CES0000000001";
        } else if (type == EmploymentType.UNEMPLOYMENT_RATE) {
            seriesId = "LNS14000000";
        } else if (type == EmploymentType.AVERAGE_HOURLY_EARNINGS_MOM) {
            seriesId = "CES0500000003";
        } else if (type == EmploymentType.AVERAGE_WEEKLY_HOURS) {
            seriesId = "CES0500000002";
        } else if (type == EmploymentType.PARTICIPATION_RATE) {
            seriesId = "LNS11300000";
        } else {
            throw new UnsupportedOperationException(
                    "Employment type not implemented: " + type
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

            String actual;
            String previous;

            if (type == EmploymentType.NFP) {

                double previousPreviousValue =
                        data.get(2)
                                .get("value")
                                .asDouble();

                double nfpChange =
                        latestValue - previousValue;

                double previousNfpChange =
                        previousValue - previousPreviousValue;

                actual =
                        String.format("%.0fK", nfpChange);

                previous =
                        String.format("%.0fK", previousNfpChange);

            } else if (type == EmploymentType.AVERAGE_HOURLY_EARNINGS_MOM) {

                double previousPreviousValue =
                        data.get(2)
                                .get("value")
                                .asDouble();

                double result =
                        ((latestValue / previousValue) - 1) * 100;

                double previousResult =
                        ((previousValue / previousPreviousValue) - 1) * 100;

                actual =
                        String.format("%.1f%%", result);

                previous =
                        String.format("%.1f%%", previousResult);

            } else if (type == EmploymentType.UNEMPLOYMENT_RATE
                    || type == EmploymentType.PARTICIPATION_RATE) {

                actual =
                        String.format("%.1f%%", latestValue);

                previous =
                        String.format("%.1f%%", previousValue);

            } else {

                actual =
                        String.format("%.1f", latestValue);

                previous =
                        String.format("%.1f", previousValue);
            }

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

        if (type == EmploymentType.NFP) {
            return "Non-Farm Employment Change";
        } else if (type == EmploymentType.UNEMPLOYMENT_RATE) {
            return "Unemployment Rate";
        } else if (type == EmploymentType.AVERAGE_HOURLY_EARNINGS_MOM) {
            return "Average Hourly Earnings m/m";
        } else if (type == EmploymentType.AVERAGE_WEEKLY_HOURS) {
            return "Average Weekly Hours";
        } else {
            return "Labor Force Participation Rate";
        }
    }
}