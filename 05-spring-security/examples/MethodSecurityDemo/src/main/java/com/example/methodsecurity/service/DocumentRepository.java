/*
 * In-memory document store used as a stand-in for a JPA repository.
 * Pre-populated with three documents owned by alice, alice, and bob respectively.
 */
package com.example.methodsecurity.service;

import com.example.methodsecurity.model.Document;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DocumentRepository {

    private static final Map<String, Document> STORE = Map.of(
        "doc-1", new Document("doc-1", "Alice's Project Plan",  "alice"),
        "doc-2", new Document("doc-2", "Alice's Meeting Notes", "alice"),
        "doc-3", new Document("doc-3", "Bob's Design Doc",      "bob")
    );

    public Optional<Document> findById(String id) {
        return Optional.ofNullable(STORE.get(id));
    }

    public List<Document> findAll() {
        return List.copyOf(STORE.values());
    }
}
