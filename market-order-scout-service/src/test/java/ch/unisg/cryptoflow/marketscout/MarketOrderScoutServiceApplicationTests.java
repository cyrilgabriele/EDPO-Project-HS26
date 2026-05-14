package ch.unisg.cryptoflow.marketscout;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "crypto.market-scout.topology.enabled=false",
        "spring.kafka.admin.auto-create=false"
})
class MarketOrderScoutServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
