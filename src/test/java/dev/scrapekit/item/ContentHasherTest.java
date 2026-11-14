package dev.scrapekit.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContentHasherTest {

    final ContentHasher hasher = new ContentHasher(new ObjectMapper());

    @Test
    @DisplayName("Field order doesn't change the hash, a changed value does")
    void canonical() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("title", "Olio");
        a.put("price", 23.88);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("price", 23.88);
        b.put("title", "Olio");
        assertThat(hasher.hash(hasher.json(a))).isEqualTo(hasher.hash(hasher.json(b))).hasSize(64);
        b.put("price", 19.99);
        assertThat(hasher.hash(hasher.json(a))).isNotEqualTo(hasher.hash(hasher.json(b)));
    }
}
