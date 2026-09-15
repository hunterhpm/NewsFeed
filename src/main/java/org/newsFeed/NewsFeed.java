package org.newsFeed;
import newsFeed.model.*;

public class NewsFeed {

    public static void main(String[] args) {

        Fetcher event = new PPI(PPIType.MOM);

        event.watchForNewEvents();

    }
}