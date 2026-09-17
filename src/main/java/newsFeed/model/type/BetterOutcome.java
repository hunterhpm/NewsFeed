package newsFeed.model.type;

public enum BetterOutcome {
    HIGHER,
    LOWER;

    public EventResult compare(
            double actual,
            double forecast,
            BetterOutcome outcome) {

        if (Double.compare(actual, forecast) == 0) {
            return EventResult.SAME;
        }

        if (outcome == BetterOutcome.HIGHER) {
            return actual > forecast
                    ? EventResult.BETTER
                    : EventResult.WORSE;
        }

        return actual < forecast
                ? EventResult.BETTER
                : EventResult.WORSE;
    }
}