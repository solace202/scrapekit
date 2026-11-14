package dev.scrapekit.scrape;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Keys {

    private Keys() {
    }

    /** Short stable key for records without a natural id (e.g. a quote = author + text). */
    public static String hash(String... parts) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            for (String p : parts) {
                md.update(p.strip().toLowerCase().getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 20);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
