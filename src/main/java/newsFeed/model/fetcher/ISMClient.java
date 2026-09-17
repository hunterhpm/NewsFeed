package newsFeed.model.fetcher;

public abstract class ISMClient extends Fetcher {

    @Override
    protected String fetchData(String url) {
        return ISMBrowserReader.read(url);
    }

    @Override
    protected void closeResources() {
        ISMBrowserReader.close();
    }

}