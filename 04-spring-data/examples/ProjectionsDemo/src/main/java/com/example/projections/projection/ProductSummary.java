/*
 * Interface-based projection for Product.
 * Spring Data generates a proxy that returns only name and price from the query.
 */
package com.example.projections.projection;

import java.math.BigDecimal;

public interface ProductSummary {
    String getName();
    BigDecimal getPrice();
}
