package newsFeed.model.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import newsFeed.model.fetcher.BEAFetcher;
import newsFeed.model.type.GDPType;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GDP extends BEAFetcher {

    private static final String GDP_RELEASES_URL =
            "https://www.bea.gov/taxonomy/term/1";

    private final GDPType type;

    public GDP(GDPType type) {
        this.type = type;
    }

    @Override
    public NewsEvent fetch() {

        if (type == GDPType.ADVANCE_QOQ) {
            return fetchGDPRelease("gdp-advance-estimate");
        }

        if (type == GDPType.PRELIM_QOQ) {
            return fetchGDPRelease("gdp-second-estimate");
        }

        if (type == GDPType.GDP_PRICE_INDEX_QOQ) {
            return fetchGDPPriceIndex();
        }

        throw new UnsupportedOperationException(
                "GDP type not implemented: " + type
        );
    }

    private NewsEvent fetchGDPRelease(String releaseType) {

        try {
            String releasesHtml = fetchPage(GDP_RELEASES_URL);

            Pattern linkPattern = Pattern.compile(
                    "href=[\"']([^\"']*/news/\\d{4}/"
                            + releaseType
                            + "[^\"']*)[\"']",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher linkMatcher = linkPattern.matcher(releasesHtml);

            if (!linkMatcher.find()) {
                throw new IllegalStateException(
                        "Could not find latest GDP release."
                );
            }

            String releaseUrl = URI.create(GDP_RELEASES_URL)
                    .resolve(linkMatcher.group(1))
                    .toString();

            String html = fetchPage(releaseUrl);

            String text = html
                    .replaceAll("(?s)<script.*?</script>", " ")
                    .replaceAll("(?s)<style.*?</style>", " ")
                    .replaceAll("<[^>]*>", " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replaceAll("\\s+", " ")
                    .trim();

            Pattern periodPattern = Pattern.compile(
                    "(\\d)(?:st|nd|rd|th) Quarter(?: and Year)? (\\d{4})",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher periodMatcher = periodPattern.matcher(text);

            if (!periodMatcher.find()) {
                throw new IllegalStateException(
                        "Could not find GDP period."
                );
            }

            String quarter = periodMatcher.group(1);
            String year = periodMatcher.group(2);

            Pattern valuePattern = Pattern.compile(
                    "Real gross domestic product \\(GDP\\)"
                            + "\\s+(increased|decreased)"
                            + "\\s+at an annual rate of"
                            + "\\s+(\\d+(?:\\.\\d+)?)"
                            + "\\s+percent",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher valueMatcher = valuePattern.matcher(text);

            if (!valueMatcher.find()) {
                throw new IllegalStateException(
                        "Could not find GDP value."
                );
            }

            String direction = valueMatcher.group(1);
            double value = Double.parseDouble(valueMatcher.group(2));

            if (direction.equalsIgnoreCase("decreased")) {
                value = -value;
            }

            String actual = String.format("%.1f%%", value);

            return new NewsEvent(
                    getLabel(),
                    actual,
                    "",
                    "BEA",
                    year + " Q" + quarter
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private NewsEvent fetchGDPPriceIndex() {

        String json = fetchData("T10107", "Q");

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode data = root
                    .get("BEAAPI")
                    .get("Results")
                    .get("Data");

            JsonNode latest = null;
            String latestPeriod = "";

            for (JsonNode item : data) {

                if (!item.get("LineNumber").asText().equals("1")) {
                    continue;
                }

                String period = item.get("TimePeriod").asText();

                if (latest == null || period.compareTo(latestPeriod) > 0) {
                    latest = item;
                    latestPeriod = period;
                }
            }

            if (latest == null) {
                throw new IllegalStateException(
                        "Could not find GDP Price Index data."
                );
            }

            String actual = latest.get("DataValue").asText() + "%";

            return new NewsEvent(
                    getLabel(),
                    actual,
                    "",
                    "BEA",
                    latestPeriod
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getLabel() {

        if (type == GDPType.ADVANCE_QOQ) {
            return "Advance GDP q/q";
        }

        if (type == GDPType.PRELIM_QOQ) {
            return "Prelim GDP q/q";
        }

        return "GDP Price Index q/q";
    }
}