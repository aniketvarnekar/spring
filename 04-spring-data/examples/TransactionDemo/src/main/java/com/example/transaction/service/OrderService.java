/*
 * Demonstrates the three core transaction scenarios.
 */
package com.example.transaction.service;

import com.example.transaction.model.EventRecord;
import com.example.transaction.repository.EventRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final EventRecordRepository eventRecordRepository;
    private final AuditService auditService;

    public OrderService(EventRecordRepository eventRecordRepository,
                        AuditService auditService) {
        this.eventRecordRepository = eventRecordRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void demonstrateRequired() {
        eventRecordRepository.save(new EventRecord("ORDER_CREATED", "required-demo"));
        System.out.println("  [OrderService] Saved order event in outer tx");

        // recordEventInSharedTx uses REQUIRED — it joins THIS transaction.
        // If this method threw after this call, both records would be rolled back.
        auditService.recordEventInSharedTx("AUDIT_REQUIRED", "required-demo");

        long count = eventRecordRepository.count();
        System.out.println("  [OrderService] Records in this tx: " + count);
    }

    @Transactional
    public void demonstrateRequiresNew() {
        eventRecordRepository.save(new EventRecord("ORDER_CREATED", "requires-new-demo"));
        System.out.println("  [OrderService] Saved order event in outer tx");

        // recordEvent uses REQUIRES_NEW — it runs in its own transaction.
        // Even if we throw an exception here after this call, the audit record is committed.
        auditService.recordEvent("AUDIT_REQUIRES_NEW", "requires-new-demo");

        // Simulate: if we threw RuntimeException here, the order record would roll back
        // but the audit record (committed in its own tx) would remain in the database.
        System.out.println("  [OrderService] Outer tx completing normally");
    }

    @Transactional
    public void demonstrateSelfInvocation() {
        eventRecordRepository.save(new EventRecord("ORDER_CREATED", "self-invocation-demo"));

        // THIS IS THE SELF-INVOCATION PROBLEM:
        // Calling innerMethod() directly bypasses the proxy — the @Transactional(REQUIRES_NEW)
        // on innerMethod is ignored and it joins THIS transaction instead.
        // To fix: move innerMethod to a separate bean.
        this.innerMethod();

        System.out.println("  [OrderService] Self-invocation: innerMethod ran in the SAME tx (REQUIRES_NEW was bypassed)");
    }

    // This @Transactional has no effect when called from within the same bean.
    // Spring's AOP proxy intercepts calls from external callers only.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void innerMethod() {
        eventRecordRepository.save(new EventRecord("INNER_EVENT", "self-invocation-demo"));
        System.out.println("  [OrderService.innerMethod] Expected a new tx — but proxy was bypassed");
    }
}
