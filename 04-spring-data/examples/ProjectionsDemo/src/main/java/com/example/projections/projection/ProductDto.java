/*
 * Class-based (DTO) projection using a Java record.
 * Used with a JPQL constructor expression in @Query — no proxy, plain object.
 */
package com.example.projections.projection;

import java.math.BigDecimal;

public record ProductDto(String name, BigDecimal price) {
}
