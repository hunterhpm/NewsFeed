package newsFeed.model.source;

import newsFeed.model.fetcher.BLSClient;
import newsFeed.model.type.BenchmarkPayrollsType;
import java.util.Objects;

public class BenchmarkPayrolls extends BLSClient {
    private final BenchmarkPayrollsType type;

    public BenchmarkPayrolls(BenchmarkPayrollsType type) {
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public NewsEvent fetch() {
        String release = "prebmk.htm";
        return fetchRelease(release, type);
    }

    @Override
    protected String getLabel() {
        return "Prelim Benchmark Payrolls Revision";
    }
}