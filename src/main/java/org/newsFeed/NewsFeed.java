package org.newsFeed;
import newsFeed.model.fetcher.Fetcher;
import newsFeed.model.fetcher.ISM;
import newsFeed.model.source.*;
import newsFeed.model.type.*;

public class NewsFeed {

    public static void main(String[] args) {

        //      ADPFetcher      // Direction JSON
        //Fetcher event = new ADP(ADPType.NON_FARM_EMPLOYMENT_CHANGE);


        //      BLSClient      // Real Chrome + browser fetch
        // Benchmark Payrolls
        //Fetcher event = new BenchmarkPayrolls(BenchmarkPayrollsType.PRELIM_REVISION);

        // CPI
        //Fetcher event = new CPI(CPIType.MOM);

        //Fetcher event = new CPI(CPIType.YOY);
        //Fetcher event = new CPI(CPIType.CORE_MOM);
        //Fetcher event = new CPI(CPIType.CORE_YOY);

        // Employment Cost Index
        //Fetcher event = new ECI(ECIType.QOQ);

        // Employment
        //Fetcher event = new Employment(EmploymentType.NFP);
        //Fetcher event = new Employment(EmploymentType.UNEMPLOYMENT_RATE);
        //Fetcher event = new Employment(EmploymentType.AVERAGE_HOURLY_EARNINGS_MOM);
        //Fetcher event = new Employment(EmploymentType.AVERAGE_WEEKLY_HOURS);
        //Fetcher event = new Employment(EmploymentType.PARTICIPATION_RATE);

        // JOLTS
        //Fetcher event = new JOLTS(JOLTSType.JOB_OPENINGS);

        // PPI
        //Fetcher event = new PPI(PPIType.MOM);
        //Fetcher event = new PPI(PPIType.YOY);
        //Fetcher event = new PPI(PPIType.CORE_MOM);
        //Fetcher event = new PPI(PPIType.CORE_YOY);


        //      BEAFetcher
        // GDP
        //Fetcher event = new GDP(GDPType.ADVANCE_QOQ);
        //Fetcher event = new GDP(GDPType.PRELIM_QOQ);
        //Fetcher event = new GDP(GDPType.GDP_PRICE_INDEX_QOQ);

        // PCE
        //Fetcher event = new PCE(PCEType.CORE_MOM);


        //      CensusFetcher
        // Durable Goods
        //Fetcher event = new DurableGoods(DurableGoodsType.MOM);
        //Fetcher event = new DurableGoods(DurableGoodsType.CORE_MOM);

        // Retail Sales
        //Fetcher event = new RetailSales(RetailSalesType.MOM);
        //Fetcher event = new RetailSales(RetailSalesType.CORE_MOM);


        // DOLFetcher
        // Unemployment Claims
        //Fetcher event = new UnemploymentClaims(ClaimsType.INITIAL_CLAIMS);


        // FedFetcher
        // FOMC
        //Fetcher event = new FOMC(FedType.FED_FUNDS_RATE);


        //      ISMClient      // Real Chrome + browser fetch
        // ISM
        //Fetcher event = new ISM(ISMType.MANUFACTURING);
        //Fetcher event = new ISM(ISMType.SERVICES);


        //      SenateFetcher
        // Fed Chair Vote
        //Fetcher event = new FedChairVote(SenateType.FED_CHAIR_NOMINATION_VOTE);

        //      SPGlobalClient
        // Flash PMI
        Fetcher event = new FlashPMI(FlashPMIType.MANUFACTURING);


        //      Waiting For Event
        // Live
        //event.watchForNewEventsWithRSSBenchmark();

        // Test Mode
        try {
            var result = event.fetchWithRSSBenchmark();
            System.out.println(result.period() + " " + result.name() + ": " + result.actual());
        } finally {
            newsFeed.model.fetcher.BLSBrowserReader.close();
        }

    }
}