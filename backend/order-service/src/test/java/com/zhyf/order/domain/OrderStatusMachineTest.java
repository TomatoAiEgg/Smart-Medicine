package com.zhyf.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhyf.common.status.OrderStatus;
import org.junit.jupiter.api.Test;

class OrderStatusMachineTest {

    private final OrderStatusMachine statusMachine = new OrderStatusMachine();

    @Test
    void shouldAllowCreatedToAuditResult() {
        assertThat(statusMachine.canTransition(OrderStatus.CREATED, OrderStatus.AUDIT_PASSED)).isTrue();
        assertThat(statusMachine.canTransition(OrderStatus.CREATED, OrderStatus.AUDIT_FAILED)).isTrue();
    }

    @Test
    void shouldAllowFulfillmentPath() {
        assertThat(statusMachine.canTransition(OrderStatus.AUDIT_PASSED, OrderStatus.RECHECKED)).isTrue();
        assertThat(statusMachine.canTransition(OrderStatus.RECHECKED, OrderStatus.DECOCTING)).isTrue();
        assertThat(statusMachine.canTransition(OrderStatus.DECOCTING, OrderStatus.CANCELLED)).isTrue();
        assertThat(statusMachine.canTransition(OrderStatus.DECOCTING, OrderStatus.DECOCTED)).isTrue();
        assertThat(statusMachine.canTransition(OrderStatus.DECOCTED, OrderStatus.PACKED)).isTrue();
        assertThat(statusMachine.canTransition(OrderStatus.PACKED, OrderStatus.SHIPPED)).isTrue();
        assertThat(statusMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.SIGNED)).isTrue();
    }

    @Test
    void shouldRejectSkippedTransition() {
        assertThatThrownBy(() -> statusMachine.requireTransition(OrderStatus.CREATED, OrderStatus.RECHECKED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Order status transition not allowed: CREATED -> RECHECKED");
    }

    @Test
    void shouldRejectTransitionFromTerminalStatus() {
        assertThat(statusMachine.canTransition(OrderStatus.AUDIT_FAILED, OrderStatus.AUDIT_PASSED)).isFalse();
        assertThat(statusMachine.canTransition(OrderStatus.SIGNED, OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void shouldParseStatusName() {
        assertThat(statusMachine.requireStatus(" AUDIT_PASSED ")).isEqualTo(OrderStatus.AUDIT_PASSED);
    }
}
