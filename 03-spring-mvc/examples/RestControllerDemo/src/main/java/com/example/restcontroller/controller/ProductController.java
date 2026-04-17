/*
 * REST controller demonstrating standard CRUD patterns.
 *
 * Design points shown here:
 *   - @RequestMapping at class level sets the base path for all methods.
 *   - ResponseEntity gives full control over status codes and headers.
 *   - POST returns 201 Created with a Location header pointing to the new resource.
 *   - PUT returns 404 if the resource does not exist (idempotent replace semantics).
 *   - DELETE returns 204 No Content on success, 404 if not found.
 *   - @Valid on @RequestBody triggers Bean Validation before the method body runs.
 *   - Optional filtering via @RequestParam with required=false.
 */
package com.example.restcontroller.controller;

import com.example.restcontroller.model.Product;
import com.example.restcontroller.model.ProductRequest;
import com.example.restcontroller.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<Product> list(
            // Optional filter — null when the parameter is absent
            @RequestParam(required = false) String category) {
        return productService.findAll(category);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> get(@PathVariable Long id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody @Valid ProductRequest request) {
        Product created = productService.create(request);
        // 201 Created with Location pointing to the new resource — standard REST convention.
        URI location = URI.create("/api/products/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id,
                                          @RequestBody @Valid ProductRequest request) {
        // PUT is idempotent — calling it multiple times produces the same result.
        // Returns 404 if the resource does not exist (unlike some APIs that create on PUT).
        return productService.update(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = productService.delete(id);
        return deleted
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
