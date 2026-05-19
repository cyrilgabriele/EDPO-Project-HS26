package ch.unisg.cryptoflow.portfolio.streams;

import ch.unisg.cryptoflow.events.OrderApprovedEvent;

import java.math.BigDecimal;

/**
 * Maps an {@link OrderApprovedEvent} to the signed quantity to fold into the
 * holdings KTable. transaction-service only emits buy approvals today, so
 * every event is treated as positive. When a sell flow is added, route on the
 * side field here.
 */
public final class SignedQuantity {

    private SignedQuantity() {}

    public static BigDecimal of(OrderApprovedEvent event) {
        if (event == null || event.amount() == null) {
            return BigDecimal.ZERO;
        }
        return event.amount();
    }
}
