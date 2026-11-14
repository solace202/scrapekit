package dev.scrapekit.item;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.scrapekit.run.RunStats.ItemCounts;
import dev.scrapekit.scrape.ScrapedItem;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upserts scraped records keyed by (job, item key). A record is NEW the first time its key is seen,
 * UPDATED when its content hash changes, and UNCHANGED otherwise.
 */
@Repository
public class ItemStore {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JdbcTemplate template;
    private final ContentHasher hasher;
    private final ObjectMapper json;

    public ItemStore(JdbcClient jdbc, JdbcTemplate template, ContentHasher hasher, ObjectMapper json) {
        this.jdbc = jdbc;
        this.template = template;
        this.hasher = hasher;
        // keep "56.50" as 56.50 (BigDecimal) when reading prices back from JSONB
        this.json = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    @Transactional
    public ItemCounts upsert(long jobId, long runId, List<ScrapedItem> batch) {
        if (batch.isEmpty()) {
            return new ItemCounts(0, 0, 0);
        }
        // last occurrence wins inside one batch
        Map<String, String> payloads = new LinkedHashMap<>();
        for (ScrapedItem i : batch) {
            payloads.put(i.key(), hasher.json(i.fields()));
        }
        Map<String, String> existing = new HashMap<>();
        jdbc.sql("SELECT item_key, content_hash FROM scraped_item WHERE job_id = ? AND item_key = ANY(?)")
                .params(jobId, payloads.keySet().toArray(String[]::new))
                .query(rs -> {
                    existing.put(rs.getString(1), rs.getString(2));
                });
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        List<Object[]> rows = new ArrayList<>(payloads.size());
        for (var e : payloads.entrySet()) {
            String hash = hasher.hash(e.getValue());
            String old = existing.get(e.getKey());
            if (old == null) {
                created++;
            } else if (!old.equals(hash)) {
                updated++;
            } else {
                unchanged++;
            }
            rows.add(new Object[]{jobId, e.getKey(), e.getValue(), hash, runId, runId});
        }
        template.batchUpdate("""
                INSERT INTO scraped_item (job_id, item_key, payload, content_hash, first_seen_run_id, last_seen_run_id)
                VALUES (?, ?, ?::jsonb, ?, ?, ?)
                ON CONFLICT (job_id, item_key) DO UPDATE SET
                    payload = EXCLUDED.payload,
                    content_hash = EXCLUDED.content_hash,
                    last_seen_run_id = EXCLUDED.last_seen_run_id,
                    last_seen_at = now(),
                    changed_at = CASE WHEN scraped_item.content_hash <> EXCLUDED.content_hash
                                      THEN now() ELSE scraped_item.changed_at END""", rows);
        return new ItemCounts(created, updated, unchanged);
    }

    public long count(long jobId) {
        return jdbc.sql("SELECT count(*) FROM scraped_item WHERE job_id = ?").param(jobId).query(Long.class).single();
    }

    public long countAll() {
        return jdbc.sql("SELECT count(*) FROM scraped_item").query(Long.class).single();
    }

    public List<StoredItem> page(long jobId, int offset, int limit) {
        return jdbc.sql("SELECT * FROM scraped_item WHERE job_id = ? ORDER BY id LIMIT ? OFFSET ?")
                .params(jobId, limit, offset).query(this::map).list();
    }

    /**
     * Streams the job's dataset in insertion order without loading it into memory.
     *
     * @param newInRun if set, only records first seen in that run ("what's new since last time")
     */
    public void stream(long jobId, Long newInRun, Consumer<StoredItem> consumer) {
        String sql = "SELECT * FROM scraped_item WHERE job_id = ?" + (newInRun != null ? " AND first_seen_run_id = ?" : "")
                + " ORDER BY id";
        Object[] args = newInRun != null ? new Object[]{jobId, newInRun} : new Object[]{jobId};
        template.query(con -> {
            var ps = con.prepareStatement(sql);
            ps.setFetchSize(500);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, rs -> {
            consumer.accept(map(rs, 0));
        });
    }

    private StoredItem map(ResultSet rs, int row) throws SQLException {
        try {
            return new StoredItem(rs.getString("item_key"), json.readValue(rs.getString("payload"), MAP),
                    ts(rs, "first_seen_at"), ts(rs, "last_seen_at"), ts(rs, "changed_at"));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new SQLException("Corrupt payload for " + rs.getString("item_key"), e);
        }
    }

    private static Instant ts(ResultSet rs, String col) throws SQLException {
        var t = rs.getObject(col, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }
}
