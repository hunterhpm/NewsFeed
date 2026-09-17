package newsFeed.model.fetcher;

public abstract class SPGlobalClient extends Fetcher {

    @Override
    protected String fetchData(String url) {
        return SPGlobalBrowserReader.read(url);
    }

    @Override
    protected void closeResources() {
        SPGlobalBrowserReader.close();
    }
}