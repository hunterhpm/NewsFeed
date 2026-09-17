package newsFeed.model.fetcher;

import newsFeed.model.source.NewsEvent;

/**
 * BLS releases are read from the live release pages in Chrome rather than the
 * delayed BLS time-series API.
 */
public abstract class BLSClient extends Fetcher {
    private static final long POLL_INTERVAL_MS = 1_000;

    protected NewsEvent fetchRelease(String release, Enum<?> metric) {
        return BLSReleaseParser.parse(
                BLSBrowserReader.read(release),
                metric,
                getLabel()
        );
    }

    @Override
    public void watchForNewEvents() {
        NewsEvent current = fetch();
        printEvent(current);

        String lastPeriod = current.period();
        int dotCount = 1;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);

                NewsEvent newest = fetch();

                if (!newest.period().equals(lastPeriod)) {
                    System.out.println();
                    System.out.println();
                    printEvent(newest);
                    lastPeriod = newest.period();
                } else {
                    String dots = ".".repeat(dotCount);
                    System.out.print("\rWaiting for new BLS event" + dots + "   ");
                    dotCount = dotCount == 3 ? 1 : dotCount + 1;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                // A transient browser/navigation failure should not kill a
                // long-running release watcher. Keep the last known period and
                // try again on the next polling cycle.
                System.err.println();
                System.err.println("BLS check failed: " + e.getMessage());
            }
        }
    }

    @Override
    protected final String fetchData(String seriesId) {
        throw new UnsupportedOperationException(
                "BLS feeds use browser release pages, not the delayed time-series API."
        );
    }

    @Override
    protected void closeResources() {
        BLSBrowserReader.close();
    }

}
