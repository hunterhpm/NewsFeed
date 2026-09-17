package newsFeed.model.source;

import newsFeed.model.fetcher.BLSClient;
import newsFeed.model.type.CPIType;
import java.util.Objects;

public class CPI extends BLSClient {
    private final CPIType type;

    public CPI(CPIType type) {
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public NewsEvent fetch() {
        String release = "cpi.t01.htm";
        return fetchRelease(release, type);
    }

    @Override
    protected String getLabel() {

        if (type == CPIType.MOM) {
            return "CPI m/m";
        } else if (type == CPIType.YOY) {
            return "CPI y/y";
        } else if (type == CPIType.CORE_MOM) {
            return "Core CPI m/m";
        } else {
            return "Core CPI y/y";
        }
    }
}