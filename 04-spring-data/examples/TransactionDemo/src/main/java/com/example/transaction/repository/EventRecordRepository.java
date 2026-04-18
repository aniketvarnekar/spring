/*
 * Repository for EventRecord — standard CRUD, no custom queries needed.
 */
package com.example.transaction.repository;

import com.example.transaction.model.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRecordRepository extends JpaRepository<EventRecord, Long> {
}
