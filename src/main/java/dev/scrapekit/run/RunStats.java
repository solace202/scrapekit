package dev.scrapekit.run;

/** Mutable counters of a run in progress. */
public class RunStats {

    int found;
    int created;
    int updated;
    int unchanged;

    public void add(ItemCounts c) {
        found += c.total();
        created += c.created();
        updated += c.updated();
        unchanged += c.unchanged();
    }

    public record ItemCounts(int created, int updated, int unchanged) {
        public int total() {
            return created + updated + unchanged;
        }
    }
}
