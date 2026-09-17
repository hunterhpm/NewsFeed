package newsFeed.model.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import newsFeed.model.fetcher.CensusFetcher;
import newsFeed.model.type.RetailSalesType;

public class RetailSales extends CensusFetcher {

    private final RetailSalesType type;

    public RetailSales(RetailSalesType type) {
        this.type = type;
    }

    @Override
    public NewsEvent fetch() {

        String categoryCode;

        if (type == RetailSalesType.MOM) {
            categoryCode = "44X72";
        } else if (type == RetailSalesType.CORE_MOM) {
            categoryCode = "44Y72";
        } else {
            throw new UnsupportedOperationException(
                    "Retail Sales type not implemented: " + type
            );
        }

        String json = fetchData(
                "marts",
                categoryCode,
                "SM"
        );

        try {
            ObjectMapper mapper = new ObjectMapper();

            JsonNode root = mapper.readTree(json);

            JsonNode latest = root.get(root.size() - 1);
            JsonNode previous = root.get(root.size() - 2);

            double latestValue = latest.get(0).asDouble();

            double previousValue = previous.get(0).asDouble();

            String period = latest.get(5).asText();

            double result = ((latestValue / previousValue) - 1) * 100;

            String actual = String.format("%.1f%%", result);

            return new NewsEvent(
                    getLabel(),
                    actual,
                    "",
                    "U.S. Census Bureau",
                    period
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getLabel() {

        if (type == RetailSalesType.MOM) {
            return "Retail Sales m/m";
        } else {
            return "CORE Retail Sales m/m";
        }
    }
}