/*
 * @Embeddable value object representing a physical address.
 *
 * An embeddable has no @Id and no table of its own. Its columns are stored
 * in the owning entity's table. Multiple embeddings of the same @Embeddable
 * in one entity require @AttributeOverride to rename columns.
 */
package com.example.entitymapping.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Address {

    @Column(nullable = false)
    private String street;

    private String city;

    @Column(length = 10)
    private String postalCode;

    @Column(length = 2)
    private String countryCode;

    // JPA requires a no-arg constructor for embeddable types
    protected Address() {}

    public Address(String street, String city, String postalCode, String countryCode) {
        this.street = street;
        this.city = city;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
    }

    public String getStreet() { return street; }
    public String getCity() { return city; }
    public String getPostalCode() { return postalCode; }
    public String getCountryCode() { return countryCode; }
}
