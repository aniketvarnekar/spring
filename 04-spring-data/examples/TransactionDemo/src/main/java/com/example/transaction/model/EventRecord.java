/*
 * Simple audit-style entity that records events.
 * Used to observe which transaction each write participates in.
 */
package com.example.transaction.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_records")
public class EventRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String event;

    @Column(nullable = false)
    private String context;

    @Column(nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    protected EventRecord() {}

    public EventRecord(String event, String context) {
        this.event = event;
        this.context = context;
    }

    public Long getId() { return id; }
    public String getEvent() { return event; }
    public String getContext() { return context; }
}
