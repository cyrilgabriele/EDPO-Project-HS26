package ch.unisg.cryptoflow.coinmetadata;

import ch.unisg.cryptoflow.coinmetadata.config.CoinMetadataProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CoinMetadataProperties.class)
public class CoinMetadataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoinMetadataServiceApplication.class, args);
    }
}
