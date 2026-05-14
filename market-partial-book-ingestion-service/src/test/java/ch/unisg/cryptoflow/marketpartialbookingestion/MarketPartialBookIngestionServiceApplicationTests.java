package ch.unisg.cryptoflow.marketpartialbookingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "binance.partial-depth.enabled=false",
        "spring.kafka.admin.auto-create=false"
})
class MarketPartialBookIngestionServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
