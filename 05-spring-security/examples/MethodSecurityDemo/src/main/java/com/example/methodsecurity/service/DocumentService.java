/*
 * Document service demonstrating multiple method security patterns.
 *
 * findById     — @PreAuthorize delegates to the custom @documentSecurity bean.
 *                Ownership check is encapsulated in DocumentSecurity.canAccess().
 *
 * listAll      — @PostFilter retains only documents owned by the caller or admins.
 *                filterObject refers to each element of the returned List<Document>.
 *
 * deleteAll    — @AdminOnly meta-annotation restricts the method to ROLE_ADMIN.
 *
 * Note: @PostFilter on listAll loads all documents from the repository and then
 * filters in memory. For large collections, prefer a query that filters at the
 * database level inside a @PreAuthorize-guarded service method.
 */
package com.example.methodsecurity.service;

import com.example.methodsecurity.model.Document;
import com.example.methodsecurity.security.AdminOnly;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @PreAuthorize("@documentSecurity.canAccess(authentication, #documentId)")
    public Document findById(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
    }

    @PostFilter("filterObject.ownerId() == authentication.name or hasRole('ADMIN')")
    public List<Document> listAll() {
        return documentRepository.findAll();
    }

    @AdminOnly
    public void deleteAll() {
        // In a real application this would delete records from the database.
    }
}
