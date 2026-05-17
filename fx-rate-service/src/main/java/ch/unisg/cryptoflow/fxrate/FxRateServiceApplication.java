package ch.unisg.cryptoflow.fxrate;

import ch.unisg.cryptoflow.fxrate.config.FxRateProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(FxRateProperties.class)
public class FxRateServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FxRateServiceApplication.class, args);
    }
}
