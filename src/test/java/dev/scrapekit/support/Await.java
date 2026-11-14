package dev.scrapekit.support;

import java.time.Duration;
import java.util.function.Supplier;

public final class Await {

    private Await() {
    }

    public static <T> T until(Supplier<T> probe, java.util.function.Predicate<T> done, Duration timeout) {
        long end = System.nanoTime() + timeout.toNanos();
        T last = probe.get();
        while (!done.test(last)) {
            if (System.nanoTime() > end) {
                throw new AssertionError("Timed out waiting, last value: " + last);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            last = probe.get();
        }
        return last;
    }
}
