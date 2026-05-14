package ch.unisg.cryptoflow.transaction.adapter.in.camunda;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceOrderBpmnTests {

    @Test
    void unmatchedBidsStillFollowThirtyFiveSecondTimerRejectionPath() throws Exception {
        try (var stream = getClass().getResourceAsStream("/placeOrder.bpmn")) {
            String bpmn = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(bpmn).contains("priceMatchedEvent");
            assertThat(bpmn).contains("<bpmn:timeDuration xsi:type=\"bpmn:tFormalExpression\">PT35S</bpmn:timeDuration>");
            assertThat(bpmn).doesNotContain("PT1M");
        }
    }
}
