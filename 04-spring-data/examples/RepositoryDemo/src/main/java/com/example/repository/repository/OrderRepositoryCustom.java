/*
 * Custom repository capability — declared as a separate interface so that
 * OrderRepository can extend both JpaRepository and this interface.
 *
 * Spring Data discovers the implementation class (OrderRepositoryCustomImpl)
 * by convention: repository interface name + "Impl" suffix.
 */
package com.example.repository.repository;

import com.example.repository.model.Order;

import java.math.BigDecimal;
import java.util.List;

public interface OrderRepositoryCustom {
    List<Order> findHighValueOrders(BigDecimal threshold);
}
