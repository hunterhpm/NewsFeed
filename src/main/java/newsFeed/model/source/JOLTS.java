package newsFeed.model.source;

import newsFeed.model.fetcher.BLSClient;
import newsFeed.model.type.JOLTSType;
import java.util.Objects;

public class JOLTS extends BLSClient {
    private final JOLTSType type;

    public JOLTS(JOLTSType type) {
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public NewsEvent fetch() {
        String release = "jolts.t01.htm";
        return fetchRelease(release, type);
    }

    @Override
    protected String getLabel() {
        return "JOLTS Job Openings";
    }
}