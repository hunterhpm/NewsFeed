package newsFeed.model.source;

import newsFeed.model.fetcher.BLSClient;
import newsFeed.model.type.ECIType;
import java.util.Objects;

public class ECI extends BLSClient {
    private final ECIType type;

    public ECI(ECIType type) {
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public NewsEvent fetch() {
        String release = "eci.nr0.htm";
        return fetchRelease(release, type);
    }

    @Override
    protected String getLabel() {
        return "Employment Cost Index q/q";
    }
}