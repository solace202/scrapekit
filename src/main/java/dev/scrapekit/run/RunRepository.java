package dev.scrapekit.run;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RunRepository {

    private final JdbcClient jdbc;

    public RunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Throws DuplicateKeyException if the job already has a RUNNING run (partial unique index). */
    public ScrapeRun start(long jobId, ScrapeRun.Trigger trigger) {
        return jdbc.sql("INSERT INTO scrape_run (job_id, trigger, status) VALUES (?, ?, 'RUNNING') RETURNING *")
                .params(jobId, trigger.name()).query(RunRepository::map).single();
    }

    public void progress(long runId, int pages, RunStats s, int retries) {
        jdbc.sql("""
                        UPDATE scrape_run SET pages_fetched = ?, items_found = ?, items_new = ?, items_updated = ?,
                               items_unchanged = ?, retries = ? WHERE id = ?""")
                .params(pages, s.found, s.created, s.updated, s.unchanged, retries, runId).update();
    }

    public void finish(long runId, ScrapeRun.Status status, int pages, RunStats s, int retries, String message) {
        jdbc.sql("""
                        UPDATE scrape_run SET status = ?, finished_at = now(), pages_fetched = ?, items_found = ?,
                               items_new = ?, items_updated = ?, items_unchanged = ?, retries = ?, message = ?
                        WHERE id = ?""")
                .params(status.name(), pages, s.found, s.created, s.updated, s.unchanged, retries, message, runId)
                .update();
    }

    /** Runs left RUNNING by a crash or restart can never finish; mark them failed so the job can run again. */
    public int failOrphans() {
        return jdbc.sql("""
                        UPDATE scrape_run SET status = 'FAILED', finished_at = now(),
                               message = 'Interrupted: the application stopped during the run'
                        WHERE status = 'RUNNING'""").update();
    }

    public Optional<ScrapeRun> findById(long id) {
        return jdbc.sql("SELECT * FROM scrape_run WHERE id = ?").param(id).query(RunRepository::map).optional();
    }

    public List<ScrapeRun> findByJob(long jobId, int limit) {
        return jdbc.sql("SELECT * FROM scrape_run WHERE job_id = ? ORDER BY id DESC LIMIT ?")
                .params(jobId, limit).query(RunRepository::map).list();
    }

    public List<ScrapeRun> recent(int limit) {
        return jdbc.sql("SELECT * FROM scrape_run ORDER BY id DESC LIMIT ?").param(limit).query(RunRepository::map).list();
    }

    public Optional<ScrapeRun> lastOf(long jobId) {
        return findByJob(jobId, 1).stream().findFirst();
    }

    static ScrapeRun map(ResultSet rs, int row) throws SQLException {
        return new ScrapeRun(rs.getLong("id"), rs.getLong("job_id"), ScrapeRun.Trigger.valueOf(rs.getString("trigger")),
                ScrapeRun.Status.valueOf(rs.getString("status")), ts(rs, "started_at"), ts(rs, "finished_at"),
                rs.getInt("pages_fetched"), rs.getInt("items_found"), rs.getInt("items_new"),
                rs.getInt("items_updated"), rs.getInt("items_unchanged"), rs.getInt("retries"), rs.getString("message"));
    }

    private static Instant ts(ResultSet rs, String col) throws SQLException {
        var t = rs.getObject(col, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }
}
