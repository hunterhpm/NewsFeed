package newsFeed.model.source;

import newsFeed.model.fetcher.FedFetcher;
import newsFeed.model.type.FedType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FOMC extends FedFetcher {

    private static final String RSS_URL = "https://www.federalreserve.gov/feeds/press_monetary.xml";

    private final FedType type;

    public FOMC(FedType type) {
        this.type = type;
    }

    @Override
    public NewsEvent fetch() {

        if (type != FedType.FED_FUNDS_RATE) {
            throw new UnsupportedOperationException(
                    "Fed type not implemented: " + type
            );
        }

        try {
            String rss = fetchData(RSS_URL);

            rss = rss.replaceFirst("^\\uFEFF", "");
            rss = rss.stripLeading();

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            Document document = factory
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(rss)));

            NodeList items = document.getElementsByTagName("item");

            String statementUrl = null;

            for (int i = 0; i < items.getLength(); i++) {

                Element item = (Element) items.item(i);

                String title = item
                        .getElementsByTagName("title")
                        .item(0)
                        .getTextContent();

                if (title.contains("Federal Reserve issues FOMC statement")) {

                    statementUrl = item
                            .getElementsByTagName("link")
                            .item(0)
                            .getTextContent();

                    break;
                }
            }

            if (statementUrl == null) {
                throw new IllegalStateException(
                        "Could not find latest FOMC statement."
                );
            }

            String html = fetchData(statementUrl);

            String text = html
                    .replaceAll("<[^>]*>", " ")
                    .replace("&nbsp;", " ")
                    .replace("&#8209;", "-")
                    .replace("-", "-")
                    .replaceAll("\\s+", " ");

            Pattern ratePattern = Pattern.compile(
                    "target range for the federal funds rate"
                            + "[^.]{0,150}?"
                            + "(\\d+(?:-\\d+/\\d+)?|\\d+(?:\\.\\d+)?)"
                            + "\\s+to\\s+"
                            + "(\\d+(?:-\\d+/\\d+)?|\\d+(?:\\.\\d+)?)"
                            + "\\s+percent",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher matcher = ratePattern.matcher(text);

            if (!matcher.find()) {
                throw new IllegalStateException(
                        "Could not find Federal Funds target range."
                );
            }

            double lowerRate = parseRate(matcher.group(1));
            double upperRate = parseRate(matcher.group(2));

            String actual = String.format("%.2f%%", upperRate);

            String period = getStatementDate(statementUrl);

            return new NewsEvent(
                    getLabel(),
                    actual,
                    "",
                    "Federal Reserve",
                    period
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private double parseRate(String value) {

        if (!value.contains("-")) {
            return Double.parseDouble(value);
        }

        String[] parts = value.split("-");
        double whole = Double.parseDouble(parts[0]);

        String[] fraction = parts[1].split("/");

        double numerator = Double.parseDouble(fraction[0]);
        double denominator = Double.parseDouble(fraction[1]);

        return whole + (numerator / denominator);
    }

    private String getStatementDate(String url) {

        Pattern pattern = Pattern.compile(
                "monetary(\\d{8})a"
        );

        Matcher matcher = pattern.matcher(url);

        if (!matcher.find()) {
            return "Unknown date";
        }

        LocalDate date = LocalDate.parse(
                matcher.group(1),
                DateTimeFormatter.BASIC_ISO_DATE
        );

        return date.toString();
    }

    @Override
    protected String getLabel() {
        return "Federal Funds Rate";
    }
}