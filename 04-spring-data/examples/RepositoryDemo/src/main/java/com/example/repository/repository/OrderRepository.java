/*
 * Repository interface combining standard JPA operations, derived queries,
 * @Query methods, and the custom fragment.
 */
package com.example.repository.repository;

import com.example.repository.model.Order;
import com.example.repository.model.OrderStatus;
import com.example.repository.projection.OrderSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, OrderRepositoryCustom {

    // Derived query — Spring Data parses "ByStatus" and generates:
    // SELECT o FROM Order o WHERE o.status = :status
    List<Order> findByStatus(OrderStatus status);

    // Derived with amount filter
    List<Order> findByStatusAndAmountGreaterThan(OrderStatus status, BigDecimal minAmount);

    // Top N — adds LIMIT clause
    List<Order> findTop3ByStatusOrderByAmountDesc(OrderStatus status);

    // Returns projection — Spring Data generates only the needed columns
    List<OrderSummary> findSummariesByStatus(OrderStatus status);

    // Optional for single-result queries — empty if not found, exception if multiple
    Optional<Order> findByExternalRef(String externalRef);

    // @Modifying required for data-changing queries
    // @Transactional required because the default repository transaction is read-only for @Query
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Order o SET o.status = 'CANCELLED' " +
           "WHERE o.status = 'PENDING' AND o.amount < :threshold")
    int cancelOldPendingOrders(@Param("threshold") BigDecimal threshold);
}
