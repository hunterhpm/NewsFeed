package newsFeed.model.source;

import newsFeed.model.fetcher.DOLFetcher;
import newsFeed.model.type.ClaimsType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnemploymentClaims extends DOLFetcher {

    private static final String RELEASE_URL =
            "https://www.dol.gov/newsroom/releases?agency=39&state=All&topic=All&year=all";

    private final ClaimsType type;

    public UnemploymentClaims(ClaimsType type) {
        this.type = type;
    }

    @Override
    public NewsEvent fetch() {

        if (type != ClaimsType.INITIAL_CLAIMS) {
            throw new UnsupportedOperationException(
                    "Claims type not implemented: " + type
            );
        }

        String html = fetchData(RELEASE_URL);

        try {
            String text = html
                    .replaceAll("(?s)<script.*?</script>", " ")
                    .replaceAll("(?s)<style.*?</style>", " ")
                    .replaceAll("<[^>]*>", " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replaceAll("\\s+", " ")
                    .trim();

            Pattern pattern = Pattern.compile(
                    "([A-Z][a-z]+ \\d{1,2}, \\d{4})"
                            + "\\s+Unemployment Insurance Weekly Claims Report"
                            + ".*?In the week ending "
                            + "([A-Z][a-z]+ \\d{1,2})"
                            + ", the advance figure for seasonally adjusted "
                            + "initial claims was ([\\d,]+)",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher matcher = pattern.matcher(text);

            if (!matcher.find()) {
                throw new IllegalStateException(
                        "Could not find latest unemployment claims data."
                );
            }

            String releaseDateText = matcher.group(1);
            String weekEndingText = matcher.group(2);
            String claimsText = matcher.group(3);

            DateTimeFormatter releaseFormatter =
                    DateTimeFormatter.ofPattern(
                            "MMMM d, yyyy",
                            Locale.US
                    );

            DateTimeFormatter weekFormatter =
                    DateTimeFormatter.ofPattern(
                            "MMMM d yyyy",
                            Locale.US
                    );

            LocalDate releaseDate = LocalDate.parse(
                    releaseDateText,
                    releaseFormatter
            );

            LocalDate weekEnding = LocalDate.parse(
                    weekEndingText + " " + releaseDate.getYear(),
                    weekFormatter
            );

            if (weekEnding.isAfter(releaseDate)) {
                weekEnding = weekEnding.minusYears(1);
            }

            int claims = Integer.parseInt(
                    claimsText.replace(",", "")
            );

            String actual = String.format(
                    "%dK",
                    claims / 1000
            );

            return new NewsEvent(
                    getLabel(),
                    actual,
                    "",
                    "DOL",
                    weekEnding.toString()
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getLabel() {
        return "Unemployment Claims";
    }
}