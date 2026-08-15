package dev.meherwerali.vaultsearch;

import dev.meherwerali.vaultsearch.config.VaultSearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties(VaultSearchProperties.class)
@SpringBootApplication
public class VaultSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(VaultSearchApplication.class, args);
    }
}
