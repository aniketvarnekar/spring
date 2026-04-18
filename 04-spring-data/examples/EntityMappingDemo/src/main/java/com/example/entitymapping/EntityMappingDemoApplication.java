/*
 * Entry point for EntityMappingDemo.
 *
 * Demonstrates:
 *   1. @Embedded / @Embeddable — Address value object stored in the Customer table.
 *   2. @MappedSuperclass — shared id/createdAt/version fields via BaseEntity.
 *   3. SINGLE_TABLE inheritance — CardPayment and BankPayment in one payments table.
 *   4. @Version for optimistic locking.
 *
 * The generated DDL is printed to show how each mapping strategy translates to schema.
 */
package com.example.entitymapping;

import com.example.entitymapping.model.Address;
import com.example.entitymapping.model.BankPayment;
import com.example.entitymapping.model.CardPayment;
import com.example.entitymapping.model.Customer;
import com.example.entitymapping.repository.CustomerRepository;
import com.example.entitymapping.repository.PaymentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;

@SpringBootApplication
public class EntityMappingDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(EntityMappingDemoApplication.class, args);
    }

    @Bean
    CommandLineRunner demo(CustomerRepository customerRepository,
                           PaymentRepository paymentRepository) {
        return args -> {
            // Demonstrate @Embedded address
            Address billing = new Address("123 Main St", "Springfield", "12345", "US");
            Customer customer = new Customer("Alice Smith", billing);
            Customer saved = customerRepository.save(customer);
            System.out.println("Customer saved: " + saved.getId() + " / version=" + saved.getVersion());

            // Demonstrate SINGLE_TABLE inheritance
            CardPayment card = new CardPayment(new BigDecimal("99.99"), "4321", "VISA");
            BankPayment bank = new BankPayment(new BigDecimal("500.00"), "****1234", "021000021");
            paymentRepository.save(card);
            paymentRepository.save(bank);

            System.out.println("Payments stored (both in the same 'payments' table):");
            paymentRepository.findAll().forEach(p ->
                    System.out.println("  " + p.getClass().getSimpleName() + " / " + p.getAmount()));
        };
    }
}
