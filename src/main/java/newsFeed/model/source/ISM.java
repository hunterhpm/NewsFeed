package newsFeed.model.fetcher;

import newsFeed.model.source.NewsEvent;
import newsFeed.model.type.ISMType;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ISM extends ISMClient {

    private static final String BASE_URL =
            "https://www.ismworld.org/supply-management-news-and-reports/reports/ism-pmi-reports/";

    private final ISMType type;

    public ISM(ISMType type) {
        this.type = type;
    }

    @Override
    public NewsEvent fetch() {

        try {
            YearMonth reportMonth = getReportMonth();

            String reportUrl = buildReportUrl(reportMonth);

            String html = fetchData(reportUrl);

            System.out.println(
                    html.substring(
                            0,
                            Math.min(html.length(), 1500)
                    )
            );

            String text = html
                    .replaceAll("(?s)<script.*?</script>", " ")
                    .replaceAll("(?s)<style.*?</style>", " ")
                    .replaceAll("<[^>]*>", " ")
                    .replace("&nbsp;", " ")
                    .replace("&reg;", " ")
                    .replace("&#174;", " ")
                    .replace("\u00A0", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            String reportName;

            if (type == ISMType.MANUFACTURING) {
                reportName = "Manufacturing";
            } else {
                reportName = "Services";
            }

            Pattern valuePattern = Pattern.compile(
                    reportName
                            + "\\s+PMI[^0-9]{0,30}"
                            + "(\\d+(?:\\.\\d+)?)"
                            + "\\s*(?:%|percent)",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher valueMatcher =
                    valuePattern.matcher(text);

            if (!valueMatcher.find()) {
                throw new IllegalStateException(
                        "Could not find ISM PMI value."
                );
            }

            double value =
                    Double.parseDouble(
                            valueMatcher.group(1)
                    );

            String actual =
                    String.format(
                            Locale.ROOT,
                            "%.1f",
                            value
                    );

            String period =
                    reportMonth.getMonth()
                            .getDisplayName(
                                    TextStyle.FULL,
                                    Locale.US
                            )
                            + " "
                            + reportMonth.getYear();

            return new NewsEvent(
                    getLabel(),
                    actual,
                    "",
                    "ISM",
                    period
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private YearMonth getReportMonth() {
        return YearMonth.now().minusMonths(1);
    }

    private String buildReportUrl(
            YearMonth reportMonth) {

        String month =
                reportMonth.getMonth()
                        .getDisplayName(
                                TextStyle.FULL,
                                Locale.US
                        )
                        .toLowerCase(Locale.US);

        String reportPath;

        if (type == ISMType.MANUFACTURING) {
            reportPath = "pmi";
        } else {
            reportPath = "services";
        }

        return BASE_URL
                + reportPath
                + "/"
                + month
                + "/";
    }

    @Override
    protected String getLabel() {

        if (type == ISMType.MANUFACTURING) {
            return "ISM Manufacturing PMI";
        } else {
            return "ISM Services PMI";
        }
    }
}