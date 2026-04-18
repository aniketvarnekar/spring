/*
 * Interface-based projection that returns a subset of Order fields.
 *
 * Spring Data generates a proxy at runtime that implements this interface and
 * maps getter calls to the corresponding query column values.
 *
 * The @Value annotation uses SpEL to compute a derived display label from two fields.
 * Note: SpEL expressions on projections cause the full entity to be loaded in the
 * persistence context, so they do not reduce the SQL columns fetched.
 */
package com.example.repository.projection;

import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;

public interface OrderSummary {

    String getExternalRef();

    BigDecimal getAmount();

    // SpEL: target is the proxied entity instance
    @Value("#{target.status.name() + ': ' + target.externalRef}")
    String getDisplayLabel();
}
