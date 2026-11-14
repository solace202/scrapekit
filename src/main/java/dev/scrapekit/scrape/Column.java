package dev.scrapekit.scrape;

/** One output column of a scraper; drives CSV/Excel headers, cell types and the UI preview. */
public record Column(String key, String header, Type type) {

    public enum Type { TEXT, NUMBER, INTEGER, BOOLEAN, URL, LIST }

    public static Column text(String key, String header) {
        return new Column(key, header, Type.TEXT);
    }

    public static Column number(String key, String header) {
        return new Column(key, header, Type.NUMBER);
    }

    public static Column integer(String key, String header) {
        return new Column(key, header, Type.INTEGER);
    }

    public static Column bool(String key, String header) {
        return new Column(key, header, Type.BOOLEAN);
    }

    public static Column url(String key, String header) {
        return new Column(key, header, Type.URL);
    }

    public static Column list(String key, String header) {
        return new Column(key, header, Type.LIST);
    }
}
