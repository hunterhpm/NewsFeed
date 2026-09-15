package org.newsFeed;
import newsFeed.model.CPI;
import newsFeed.model.CpiType;
import newsFeed.model.PPI;
import newsFeed.model.PpiType;

public class News {

    public static void main(String[] args) {

        CPI cpi = new CPI(CpiType.MOM);

        cpi.watchForNewEvents();

        PPI ppi = new PPI(PpiType.MOM);

        ppi.watchForNewEvents();

    }
}