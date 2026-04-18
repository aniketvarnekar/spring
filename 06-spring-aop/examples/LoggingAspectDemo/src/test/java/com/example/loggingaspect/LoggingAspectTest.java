/*
 * Tests verifying that LoggingAspect intercepts OrderService calls.
 *
 * Checks that:
 *   - Successful method calls are not affected by the aspect (results pass through unchanged).
 *   - Exceptions are re-thrown (the aspect does not swallow them).
 *
 * The actual logging output is not asserted — testing log output in unit tests
 * is brittle. The tests confirm that the aspect does not alter observable behavior.
 */
package com.example.loggingaspect;

import com.example.loggingaspect.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LoggingAspectTest {

    @Autowired
    private OrderService orderService;

    @Test
    void findAll_returnsOrders() {
        List<String> orders = orderService.findAll();
        assertThat(orders).isNotEmpty();
    }

    @Test
    void findById_withValidId_returnsOrder() {
        String order = orderService.findById(1L);
        assertThat(order).contains("Laptop");
    }

    @Test
    void findById_withInvalidId_throwsAndExceptionPropagates() {
        // Aspect re-throws the exception — caller receives it unchanged.
        assertThatThrownBy(() -> orderService.findById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    @Test
    void create_returnsCreatedOrder() {
        String order = orderService.create("Keyboard");
        assertThat(order).contains("Keyboard");
    }
}
