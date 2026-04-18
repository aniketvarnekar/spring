/*
 * Repository for the Customer entity.
 */
package com.example.entitymapping.repository;

import com.example.entitymapping.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
