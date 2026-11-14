package dev.scrapekit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ScrapeKitApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScrapeKitApplication.class, args);
    }
}
