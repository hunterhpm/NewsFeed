package newsFeed.model.source;

import newsFeed.model.fetcher.BLSClient;
import newsFeed.model.type.EmploymentType;
import java.util.Objects;

public class Employment extends BLSClient {
    private final EmploymentType type;

    public Employment(EmploymentType type) {
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public NewsEvent fetch() {
        String release = (type == EmploymentType.UNEMPLOYMENT_RATE || type == EmploymentType.PARTICIPATION_RATE) ? "empsit.a.htm" : "empsit.b.htm";
        return fetchRelease(release, type);
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