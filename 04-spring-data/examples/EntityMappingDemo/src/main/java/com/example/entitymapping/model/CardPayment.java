/*
 * Card payment subtype — stored in the same 'payments' table with payment_type = 'CARD'.
 * The cardLastFour and cardBrand columns are NULL for BankPayment rows.
 */
package com.example.entitymapping.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

@Entity
@DiscriminatorValue("CARD")
public class CardPayment extends Payment {

    @Column(length = 4)
    private String cardLastFour;

    @Column(length = 20)
    private String cardBrand;

    protected CardPayment() {}

    public CardPayment(BigDecimal amount, String cardLastFour, String cardBrand) {
        super(amount);
        this.cardLastFour = cardLastFour;
        this.cardBrand = cardBrand;
    }

    public String getCardLastFour() { return cardLastFour; }
    public String getCardBrand() { return cardBrand; }
}
