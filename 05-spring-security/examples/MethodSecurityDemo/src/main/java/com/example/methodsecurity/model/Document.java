/*
 * Domain model representing a document with an owner.
 * The ownerId field is compared against authentication.name in SpEL expressions
 * for ownership-based access control.
 */
package com.example.methodsecurity.model;

public record Document(String id, String title, String ownerId) {
}
