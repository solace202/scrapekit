package dev.scrapekit.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal robots.txt support: picks the group for our user agent (or {@code *}) and applies the
 * longest matching Allow/Disallow rule. A missing or unreadable robots.txt allows everything.
 */
public class RobotsPolicy {

    record Rule(String prefix, boolean allow) {
    }

    private final HttpClient http;
    private final String userAgent;
    private final Duration timeout;
    private final Map<String, List<Rule>> cache = new ConcurrentHashMap<>();

    public RobotsPolicy(HttpClient http, String userAgent, Duration timeout) {
        this.http = http;
        this.userAgent = userAgent;
        this.timeout = timeout;
    }

    public boolean isAllowed(URI uri) {
        String origin = uri.getScheme() + "://" + uri.getRawAuthority();
        List<Rule> rules = cache.computeIfAbsent(origin, this::load);
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }
        return isAllowed(rules, path);
    }

    static boolean isAllowed(List<Rule> rules, String path) {
        Rule best = null;
        for (Rule r : rules) {
            if (path.startsWith(r.prefix())
                    && (best == null || r.prefix().length() > best.prefix().length()
                    || (r.prefix().length() == best.prefix().length() && r.allow()))) {
                best = r;
            }
        }
        return best == null || best.allow();
    }

    private List<Rule> load(String origin) {
        try {
            var req = HttpRequest.newBuilder(URI.create(origin + "/robots.txt"))
                    .timeout(timeout).header("User-Agent", userAgent).GET().build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? parse(resp.body(), userAgent) : List.of();
        } catch (IOException e) {
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /** Returns the rules of the most specific group: our product token if present, else {@code *}. */
    static List<Rule> parse(String robots, String userAgent) {
        String token = userAgent.split("[/ ]")[0].toLowerCase(Locale.ROOT);
        List<Rule> star = new ArrayList<>();
        List<Rule> ours = new ArrayList<>();
        boolean ourGroupSeen = false;
        List<String> agents = new ArrayList<>();
        boolean inRules = false;
        List<Rule> current = new ArrayList<>();
        for (String raw : (robots + "\nuser-agent: __end__").split("\\R")) {
            String line = raw.replaceAll("#.*", "").trim();
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                if (inRules) {               // a new group starts: flush the previous one
                    if (agents.contains(token)) {
                        ours.addAll(current);
                        ourGroupSeen = true;
                    } else if (agents.contains("*")) {
                        star.addAll(current);
                    }
                    agents.clear();
                    current = new ArrayList<>();
                    inRules = false;
                }
                agents.add(value.toLowerCase(Locale.ROOT));
            } else if (key.equals("allow") || key.equals("disallow")) {
                inRules = true;
                if (!value.isEmpty()) {      // "Disallow:" with no value means allow all
                    current.add(new Rule(value.replace("*", ""), key.equals("allow")));
                }
            }
        }
        return ourGroupSeen ? ours : star;
    }
}
