package dev.scrapekit.http;

/** A URL could not be fetched: non-retryable status, retries exhausted, or blocked by robots.txt. */
public class FetchException extends RuntimeException {

    private final int status;

    public FetchException(String message, int status) {
        super(message);
        this.status = status;
    }

    public FetchException(String message, Throwable cause) {
        super(message, cause);
        this.status = -1;
    }

    /** HTTP status of the last attempt, or -1 for network errors and robots.txt blocks. */
    public int status() {
        return status;
    }
}
