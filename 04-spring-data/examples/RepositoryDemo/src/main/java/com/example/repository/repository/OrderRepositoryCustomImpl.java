/*
 * Implementation of the custom repository fragment.
 *
 * Uses EntityManager directly for queries that need Criteria API or native SQL
 * features not expressible through derived query methods or @Query annotations.
 *
 * The class name suffix "Impl" matches the default repositoryImplementationPostfix
 * configured in @EnableJpaRepositories. Spring Data merges this implementation
 * into the OrderRepository proxy automatically.
 */
package com.example.repository.repository;

import com.example.repository.model.Order;
import com.example.repository.model.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.util.List;

public class OrderRepositoryCustomImpl implements OrderRepositoryCustom {

    // @PersistenceContext injects the transaction-scoped EntityManager proxy.
    // Each transaction gets its own EntityManager; the proxy routes to the correct one.
    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Order> findHighValueOrders(BigDecimal threshold) {
        // Criteria API usage — type-safe but verbose; appropriate for dynamic queries.
        // For static queries, @Query is cleaner.
        return em.createQuery(
                "SELECT o FROM Order o WHERE o.amount >= :threshold AND o.status = :status " +
                "ORDER BY o.amount DESC",
                Order.class)
                .setParameter("threshold", threshold)
                .setParameter("status", OrderStatus.COMPLETED)
                .getResultList();
    }
}
