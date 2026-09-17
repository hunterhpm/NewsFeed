package newsFeed.model.source;

import newsFeed.model.fetcher.SenateFetcher;
import newsFeed.model.type.SenateType;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class FedChairVote extends SenateFetcher {

    private final SenateType type;

    public FedChairVote(SenateType type) {
        this.type = type;
    }

    @Override
    public NewsEvent fetch() {

        if (type != SenateType.FED_CHAIR_NOMINATION_VOTE) {
            throw new UnsupportedOperationException(
                    "Senate type not implemented: " + type
            );
        }

        try {
            int year = LocalDate.now().getYear();

            int congress =
                    ((year - 1789) / 2) + 1;

            int session =
                    year % 2 == 1 ? 1 : 2;

            String menuUrl =
                    "https://www.senate.gov/legislative/LIS/roll_call_lists/"
                            + "vote_menu_"
                            + congress
                            + "_"
                            + session
                            + ".xml";

            String menuXml = fetchData(menuUrl);

            if (menuXml.startsWith("\uFEFF")) {
                menuXml = menuXml.substring(1);
            }

            menuXml = menuXml.stripLeading();

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            Document menuDocument = factory
                    .newDocumentBuilder()
                    .parse(
                            new InputSource(
                                    new StringReader(menuXml)
                            )
                    );

            NodeList votes =
                    menuDocument.getElementsByTagName("vote");

            String voteNumber = null;

            for (int i = 0; i < votes.getLength(); i++) {

                Element vote =
                        (Element) votes.item(i);

                String voteText =
                        vote.getTextContent()
                                .toLowerCase();

                if (voteText.contains("federal reserve")
                        && voteText.contains("chairman")
                        && voteText.contains("nomination")) {

                    voteNumber = vote
                            .getElementsByTagName("vote_number")
                            .item(0)
                            .getTextContent()
                            .trim();

                    break;
                }
            }

            if (voteNumber == null) {
                throw new IllegalStateException(
                        "Could not find Fed Chair nomination vote."
                );
            }

            voteNumber = String.format(
                    "%05d",
                    Integer.parseInt(voteNumber)
            );

            String voteUrl =
                    "https://www.senate.gov/legislative/LIS/roll_call_votes/"
                            + "vote"
                            + congress
                            + session
                            + "/vote_"
                            + congress
                            + "_"
                            + session
                            + "_"
                            + voteNumber
                            + ".xml";

            String voteXml = fetchData(voteUrl);

            if (voteXml.startsWith("\uFEFF")) {
                voteXml = voteXml.substring(1);
            }

            voteXml = voteXml.stripLeading();

            Document voteDocument = factory
                    .newDocumentBuilder()
                    .parse(
                            new InputSource(
                                    new StringReader(voteXml)
                            )
                    );

            String voteResult =
                    voteDocument
                            .getElementsByTagName("vote_result")
                            .item(0)
                            .getTextContent()
                            .trim();

            String voteDate =
                    voteDocument
                            .getElementsByTagName("vote_date")
                            .item(0)
                            .getTextContent()
                            .trim();

            String actual;

            if (voteResult.toLowerCase().contains("confirmed")) {
                actual = "Confirmed";
            } else if (voteResult.toLowerCase().contains("rejected")
                    || voteResult.toLowerCase().contains("failed")) {
                actual = "Rejected";
            } else {
                actual = voteResult;
            }

            /*
             * Senate XML sometimes contains extra whitespace:
             *
             * May 13, 2026,  01:59 PM
             *
             * Normalize it before parsing.
             */
            voteDate = voteDate
                    .replace('\u00A0', ' ')
                    .replaceAll("\\s+", " ")
                    .trim();

            DateTimeFormatter inputFormatter =
                    DateTimeFormatter.ofPattern(
                            "MMMM d, yyyy, hh:mm a",
                            Locale.US
                    );

            LocalDateTime dateTime =
                    LocalDateTime.parse(
                            voteDate,
                            inputFormatter
                    );

            String period =
                    dateTime
                            .toLocalDate()
                            .toString();

            return new NewsEvent(
                    getLabel(),
                    actual,
                    "",
                    "U.S. Senate",
                    period
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getLabel() {
        return "Fed Chair Nomination Vote";
    }
}