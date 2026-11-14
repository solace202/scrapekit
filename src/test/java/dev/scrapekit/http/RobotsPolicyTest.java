package dev.scrapekit.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RobotsPolicyTest {

    static final String UA = "ScrapeKit/0.1 (+https://github.com/solace202/scrapekit)";

    @Test
    @DisplayName("The * group applies when there is no group for our user agent")
    void starGroup() {
        var rules = RobotsPolicy.parse("User-agent: *\nDisallow: /admin/\nDisallow: /cart # checkout\n", UA);
        assertThat(RobotsPolicy.isAllowed(rules, "/catalogue/page-2.html")).isTrue();
        assertThat(RobotsPolicy.isAllowed(rules, "/admin/users")).isFalse();
        assertThat(RobotsPolicy.isAllowed(rules, "/cart?id=1")).isFalse();
    }

    @Test
    @DisplayName("A group naming ScrapeKit wins over *, and the longest matching rule decides")
    void specificGroupAndLongestMatch() {
        var rules = RobotsPolicy.parse("""
                User-agent: *
                Disallow: /

                User-agent: Googlebot
                User-agent: scrapekit
                Disallow: /private/
                Allow: /private/public-report
                """, UA);
        assertThat(RobotsPolicy.isAllowed(rules, "/catalogue/")).isTrue();
        assertThat(RobotsPolicy.isAllowed(rules, "/private/x")).isFalse();
        assertThat(RobotsPolicy.isAllowed(rules, "/private/public-report.csv")).isTrue();
    }

    @Test
    @DisplayName("An empty Disallow allows everything")
    void emptyDisallow() {
        var rules = RobotsPolicy.parse("User-agent: *\nDisallow:\n", UA);
        assertThat(RobotsPolicy.isAllowed(rules, "/anything")).isTrue();
    }
}
