package org.newsFeed;
import newsFeed.model.CPI;
import newsFeed.model.CpiType;

public class News {

    public static void main(String[] args) {

        CPI cpi = new CPI(CpiType.MOM);

        cpi.watchForNewEvents();

    }
}