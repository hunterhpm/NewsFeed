package newsFeed.model.source;
import newsFeed.model.fetcher.SPGlobalClient;
import newsFeed.model.type.FlashPMIType;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlashPMI extends SPGlobalClient {

    private static final String RELEASES_URL =
            "https://www.pmi.spglobal.com/Public/Release/PressReleases?language=en";

    private final FlashPMIType type;

    public FlashPMI(FlashPMIType type) {
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public NewsEvent fetch() {

        try {
            String releasesHtml =
                    fetchData(RELEASES_URL);

            String releaseUrl =
                    findLatestUSFlashPMIRelease(releasesHtml);

            String releaseHtml =
                    fetchData(releaseUrl);

            String text =
                    cleanHtml(releaseHtml);

            String period =
                    findPeriod(text);

            double value =
                    findValue(text);

            String actual =
                    String.format(
                            Locale.ROOT,
                            "%.1f",
                            value
                    );

            return new NewsEvent(
                    getLabel(),
                    actual,
                    "",
                    "S&P Global",
                    period
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String findLatestUSFlashPMIRelease(
            String html) {

        /*
         * Find the exact US Flash PMI title first,
         * then grab the first PressRelease link
         * that follows it.
         *
         * This prevents grabbing a neighboring
         * country's "View More" link.
         */
        Pattern pattern = Pattern.compile(
                "S(?:&amp;|&)P\\s+Global\\s+Flash\\s+US\\s+PMI"
                        + "[\\s\\S]{0,2500}?"
                        + "href=[\"']"
                        + "([^\"']*/Public/Home/PressRelease/[a-zA-Z0-9]+)"
                        + "[\"']",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher =
                pattern.matcher(html);

        if (!matcher.find()) {
            throw new IllegalStateException(
                    "Could not find latest S&P Global "
                            + "Flash US PMI release."
            );
        }

        String releaseUrl =
                URI.create(RELEASES_URL)
                        .resolve(matcher.group(1))
                        .toString();

        System.out.println(
                "S&P Global Flash PMI URL: "
                        + releaseUrl
        );

        return releaseUrl;
    }

    private String findPeriod(
            String text) {

        /*
         * Look for month + year in the release.
         *
         * Example:
         * August 2026
         */
        Pattern pattern = Pattern.compile(
                "([A-Z][a-z]+\\s+\\d{4})",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher =
                pattern.matcher(text);

        if (!matcher.find()) {
            throw new IllegalStateException(
                    "Could not find Flash PMI period."
            );
        }

        return matcher.group(1);
    }

    private double findValue(String text) {

        if (type != FlashPMIType.MANUFACTURING) {
            throw new UnsupportedOperationException(
                    "Flash PMI type not implemented: " + type
            );
        }

        Pattern pattern = Pattern.compile(
                "Flash\\s+US\\s+Manufacturing\\s+PMI"
                        + ".{0,80}?"
                        + "(\\d{2}(?:\\.\\d+)?)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(text);

        if (!matcher.find()) {
            throw new IllegalStateException(
                    "Could not find Flash US Manufacturing PMI value."
            );
        }

        return Double.parseDouble(
                matcher.group(1)
        );
    }

    private String cleanHtml(
            String html) {

        return html
                .replaceAll(
                        "(?is)<script.*?</script>",
                        " "
                )
                .replaceAll(
                        "(?is)<style.*?</style>",
                        " "
                )
                .replaceAll(
                        "<[^>]*>",
                        " "
                )
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&reg;", "")
                .replace("&#174;", "")
                .replace("®", "")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Override
    protected String getLabel() {
        return "Flash Manufacturing PMI";
    }
}