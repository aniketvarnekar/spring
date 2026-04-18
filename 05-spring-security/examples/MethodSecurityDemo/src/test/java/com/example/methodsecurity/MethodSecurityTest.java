/*
 * Tests for method-level security in DocumentService.
 *
 * Uses @WithMockUser to simulate different authenticated principals without
 * a real authentication flow. Each test verifies that the correct authorization
 * decision is made for the annotated service methods.
 */
package com.example.methodsecurity;

import com.example.methodsecurity.model.Document;
import com.example.methodsecurity.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class MethodSecurityTest {

    @Autowired
    private DocumentService documentService;

    // @PostFilter: listAll() returns only alice's documents when called as alice
    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void listAll_asAlice_returnsOnlyAliceDocuments() {
        List<Document> docs = documentService.listAll();
        assertThat(docs).allMatch(d -> d.ownerId().equals("alice"));
        assertThat(docs).hasSize(2);
    }

    // @PostFilter: listAll() returns all documents when called as an admin
    @Test
    @WithMockUser(username = "carol", roles = "ADMIN")
    void listAll_asAdmin_returnsAllDocuments() {
        List<Document> docs = documentService.listAll();
        assertThat(docs).hasSize(3);
    }

    // @PreAuthorize with custom bean: alice can access her own document
    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void findById_asOwner_returnsDocument() {
        Document doc = documentService.findById("doc-1");
        assertThat(doc.ownerId()).isEqualTo("alice");
    }

    // @PreAuthorize with custom bean: bob cannot access alice's document
    @Test
    @WithMockUser(username = "bob", roles = "USER")
    void findById_asNonOwner_throwsAccessDeniedException() {
        assertThatThrownBy(() -> documentService.findById("doc-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // @PreAuthorize with custom bean: admin can access any document
    @Test
    @WithMockUser(username = "carol", roles = "ADMIN")
    void findById_asAdmin_returnsDocument() {
        Document doc = documentService.findById("doc-3");
        assertThat(doc.ownerId()).isEqualTo("bob");
    }

    // @AdminOnly meta-annotation: deleteAll denied for regular users
    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void deleteAll_asUser_throwsAccessDeniedException() {
        assertThatThrownBy(() -> documentService.deleteAll())
                .isInstanceOf(AccessDeniedException.class);
    }

    // @AdminOnly meta-annotation: deleteAll permitted for admins
    @Test
    @WithMockUser(username = "carol", roles = "ADMIN")
    void deleteAll_asAdmin_succeeds() {
        documentService.deleteAll(); // no exception
    }
}
