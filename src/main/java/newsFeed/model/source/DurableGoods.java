package newsFeed.model.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import newsFeed.model.fetcher.CensusFetcher;
import newsFeed.model.type.DurableGoodsType;

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

        String json = fetchData("advm3", categoryCode, "NO");

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode latest = null;
            JsonNode previous = null;

            String latestPeriod = "";
            String previousPeriod = "";

            // Start at 1 because row 0 contains the column names.
            for (int i = 1; i < root.size(); i++) {

                JsonNode item = root.get(i);
                String period = item.get(5).asText();

                if (latest == null || period.compareTo(latestPeriod) > 0) {

                    previous = latest;
                    previousPeriod = latestPeriod;

                    latest = item;
                    latestPeriod = period;

                } else if (previous == null || period.compareTo(previousPeriod) > 0) {

                    previous = item;
                    previousPeriod = period;
                }
            }

            if (latest == null || previous == null) {
                throw new IllegalStateException(
                        "Could not find the latest two Durable Goods periods."
                );
            }

            double latestValue = latest.get(0).asDouble();
            double previousValue = previous.get(0).asDouble();

            double result = ((latestValue / previousValue) - 1) * 100;

            String actual = String.format("%.1f%%", result);

            return new NewsEvent(
                    getLabel(),
                    actual,
                    "",
                    "U.S. Census Bureau",
                    latestPeriod
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