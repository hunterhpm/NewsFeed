package newsFeed.model.source;

public record NewsEvent(
        String name,
        String actual,
        String previous,
        String source,
        String period
) {
}
