package dev.scrapekit.job;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JobRepository {

    private final JdbcClient jdbc;

    public JobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ScrapeJob> findAll() {
        return jdbc.sql("SELECT * FROM scrape_job ORDER BY id").query(JobRepository::map).list();
    }

    public Optional<ScrapeJob> findById(long id) {
        return jdbc.sql("SELECT * FROM scrape_job WHERE id = ?").param(id).query(JobRepository::map).optional();
    }

    public ScrapeJob insert(String name, String source, String startUrl, int maxPages, int delayMs, String cron, boolean enabled) {
        return jdbc.sql("""
                        INSERT INTO scrape_job (name, source, start_url, max_pages, delay_ms, cron, enabled)
                        VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING *""")
                .params(name, source, startUrl, maxPages, delayMs, cron, enabled)
                .query(JobRepository::map).single();
    }

    public ScrapeJob update(long id, String name, String source, String startUrl, int maxPages, int delayMs,
                            String cron, boolean enabled) {
        return jdbc.sql("""
                        UPDATE scrape_job SET name = ?, source = ?, start_url = ?, max_pages = ?, delay_ms = ?, cron = ?,
                               enabled = ?, updated_at = now()
                        WHERE id = ? RETURNING *""")
                .params(name, source, startUrl, maxPages, delayMs, cron, enabled, id)
                .query(JobRepository::map).optional()
                .orElseThrow(() -> new NotFoundException("Job " + id + " not found"));
    }

    public boolean delete(long id) {
        return jdbc.sql("DELETE FROM scrape_job WHERE id = ?").param(id).update() > 0;
    }

    static ScrapeJob map(ResultSet rs, int row) throws SQLException {
        return new ScrapeJob(rs.getLong("id"), rs.getString("name"), rs.getString("source"), rs.getString("start_url"),
                rs.getInt("max_pages"), rs.getInt("delay_ms"), rs.getString("cron"), rs.getBoolean("enabled"),
                ts(rs, "created_at"), ts(rs, "updated_at"));
    }

    static Instant ts(ResultSet rs, String col) throws SQLException {
        var t = rs.getObject(col, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }
}
