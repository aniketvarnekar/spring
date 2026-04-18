/*
 * Customer entity embedding an Address value object.
 * Extends BaseEntity for id, version, and createdAt.
 */
package com.example.entitymapping.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

    @Column(nullable = false)
    private String name;

    // The Address fields (street, city, postalCode, countryCode) are stored
    // directly in the customers table — no join required to access them.
    @Embedded
    private Address billingAddress;

    protected Customer() {}

    public Customer(String name, Address billingAddress) {
        this.name = name;
        this.billingAddress = billingAddress;
    }

    public String getName() { return name; }
    public Address getBillingAddress() { return billingAddress; }
}
