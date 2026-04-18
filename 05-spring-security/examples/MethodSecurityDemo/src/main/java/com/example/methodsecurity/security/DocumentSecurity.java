/*
 * Custom security bean for document ownership checks.
 *
 * Referenced in @PreAuthorize SpEL expressions via @documentSecurity.canAccess(...).
 * Encapsulates complex authorization logic that would be unreadable inline in SpEL.
 *
 * The bean name "documentSecurity" is used as the SpEL reference:
 *   @PreAuthorize("@documentSecurity.canAccess(authentication, #documentId)")
 */
package com.example.methodsecurity.security;

import com.example.methodsecurity.service.DocumentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("documentSecurity")
public class DocumentSecurity {

    private final DocumentRepository documentRepository;

    public DocumentSecurity(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Returns true if the authenticated user is an admin or the owner of the document.
     */
    public boolean canAccess(Authentication authentication, String documentId) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return true;
        }
        return documentRepository.findById(documentId)
                .map(doc -> doc.ownerId().equals(authentication.getName()))
                .orElse(false);
    }
}
