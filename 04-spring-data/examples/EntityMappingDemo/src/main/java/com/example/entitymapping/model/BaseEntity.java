/*
 * @MappedSuperclass providing id, version (optimistic lock), and createdAt
 * to all inheriting entities.
 *
 * @MappedSuperclass causes no table to be created for this class. Its mapped
 * fields are included in the table of each concrete subclass entity.
 */
package com.example.entitymapping.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Version enables optimistic locking. Hibernate increments this value on each UPDATE.
    // A stale update (concurrent modification) throws OptimisticLockException.
    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
