/*
 * Audit service with REQUIRES_NEW propagation — each audit record commits
 * in its own independent transaction, decoupled from the caller's transaction.
 */
package com.example.transaction.service;

import com.example.transaction.model.EventRecord;
import com.example.transaction.repository.EventRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final EventRecordRepository eventRecordRepository;

    public AuditService(EventRecordRepository eventRecordRepository) {
        this.eventRecordRepository = eventRecordRepository;
    }

    // REQUIRES_NEW: suspends the caller's transaction and starts a new one.
    // The audit record commits even if the calling transaction rolls back.
    // Use this for audit trails, notifications, and other side effects that must persist
    // regardless of the main operation's outcome.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(String event, String context) {
        eventRecordRepository.save(new EventRecord(event, context));
        System.out.println("  [AuditService] Recorded event: " + event + " (own tx)");
    }

    // REQUIRED: joins the caller's transaction — rolled back with it if the caller fails.
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordEventInSharedTx(String event, String context) {
        eventRecordRepository.save(new EventRecord(event, context));
        System.out.println("  [AuditService] Recorded event: " + event + " (shared tx)");
    }
}
