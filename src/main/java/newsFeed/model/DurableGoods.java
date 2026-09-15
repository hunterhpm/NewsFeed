package newsFeed.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DurableGoods extends CensusFetcher {

    private final DurableGoodsType type;

    public DurableGoods(DurableGoodsType type) {
        this.type = type;
    }

    @Override
    public NewsEvent fetch() {

        String categoryCode;

        if (type == DurableGoodsType.MOM) {
            categoryCode = "MDM";
        } else if (type == DurableGoodsType.CORE_MOM) {
            categoryCode = "DXT";
        } else {
            throw new UnsupportedOperationException(
                    "Durable Goods type not implemented: " + type
            );
        }

        String json = fetchData(
                "advm3",
                categoryCode,
                "NO"
        );

        try {
            ObjectMapper mapper = new ObjectMapper();

            JsonNode root = mapper.readTree(json);

            JsonNode latest = root.get(root.size() - 1);
            JsonNode previous = root.get(root.size() - 2);

            double latestValue =
                    latest.get(0).asDouble();

            double previousValue =
                    previous.get(0).asDouble();

            String period =
                    latest.get(1).asText();

            double result =
                    ((latestValue / previousValue) - 1) * 100;

            String actual =
                    String.format("%.1f%%", result);

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

        if (type == DurableGoodsType.MOM) {
            return "Durable Goods Orders m/m";
        } else {
            return "Core Durable Goods Orders m/m";
        }
    }
}