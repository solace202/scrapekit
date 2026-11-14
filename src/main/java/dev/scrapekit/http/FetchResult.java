package dev.scrapekit.http;

import java.net.URI;

/**
 * @param uri      the URL after redirects
 * @param attempts how many requests were needed (1 = no retry)
 */
public record FetchResult(URI uri, int status, String contentType, String body, int attempts) {

    public int retries() {
        return attempts - 1;
    }
}
