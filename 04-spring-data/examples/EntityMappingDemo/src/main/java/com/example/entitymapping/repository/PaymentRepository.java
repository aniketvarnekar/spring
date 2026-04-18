/*
 * Repository for the Payment hierarchy. Queries return polymorphic results —
 * Hibernate instantiates the correct subtype based on the discriminator value.
 */
package com.example.entitymapping.repository;

import com.example.entitymapping.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
