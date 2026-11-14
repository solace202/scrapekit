-- Demo jobs against the public scraping sandboxes at toscrape.com.
INSERT INTO scrape_job (name, source, start_url, max_pages, delay_ms, cron) VALUES
    ('Books catalogue (all categories)', 'books',      'https://books.toscrape.com/',             120, 300, '0 0 6 * * *'),
    ('Quotes (HTML pages)',              'quotes',     'https://quotes.toscrape.com/',             20, 300, '0 30 */6 * * *'),
    ('Quotes (JSON API)',                'quotes-api', 'https://quotes.toscrape.com/api/quotes',   20, 300, NULL);
