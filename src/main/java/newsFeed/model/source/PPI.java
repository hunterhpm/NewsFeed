package newsFeed.model.source;

import newsFeed.model.fetcher.BLSClient;
import newsFeed.model.type.PPIType;
import java.util.Objects;

public class PPI extends BLSClient {
    private final PPIType type;

    public PPI(PPIType type) {
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public NewsEvent fetch() {
        String release = "ppi.t01.htm";
        return fetchRelease(release, type);
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