package dev.scrapekit.item;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.springframework.stereotype.Component;

/** SHA-256 of the canonical JSON (sorted keys), so field order never makes a record look changed. */
@Component
public class ContentHasher {

    private final ObjectMapper canonical;

    public ContentHasher(ObjectMapper mapper) {
        this.canonical = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public String json(Map<String, Object> fields) {
        try {
            return canonical.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Item is not serialisable", e);
        }
    }

    public String hash(String canonicalJson) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(canonicalJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
