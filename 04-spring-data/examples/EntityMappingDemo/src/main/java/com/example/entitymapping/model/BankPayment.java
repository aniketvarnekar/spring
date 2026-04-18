/*
 * Bank transfer payment subtype — stored in the same 'payments' table with payment_type = 'BANK'.
 */
package com.example.entitymapping.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

@Entity
@DiscriminatorValue("BANK")
public class BankPayment extends Payment {

    @Column(length = 30)
    private String accountNumber;

    @Column(length = 20)
    private String routingNumber;

    protected BankPayment() {}

    public BankPayment(BigDecimal amount, String accountNumber, String routingNumber) {
        super(amount);
        this.accountNumber = accountNumber;
        this.routingNumber = routingNumber;
    }

    public String getAccountNumber() { return accountNumber; }
    public String getRoutingNumber() { return routingNumber; }
}
