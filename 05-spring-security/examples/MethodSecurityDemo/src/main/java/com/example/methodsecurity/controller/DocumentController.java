/*
 * REST controller for document operations.
 * Security is enforced in DocumentService, not here — the controller is thin.
 */
package com.example.methodsecurity.controller;

import com.example.methodsecurity.model.Document;
import com.example.methodsecurity.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<Document> list() {
        return documentService.listAll();
    }

    @GetMapping("/{id}")
    public Document get(@PathVariable String id) {
        return documentService.findById(id);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        documentService.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
