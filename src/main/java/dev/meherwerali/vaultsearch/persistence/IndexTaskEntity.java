package dev.meherwerali.vaultsearch.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "index_tasks")
public class IndexTaskEntity {

    @Id
    @Column(name = "note_id", length = 64)
    private String noteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskOperation operation;

    @Column(name = "expected_hash", length = 64)
    private String expectedHash;

    @Lob
    @Column(name = "payload_json", columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TaskStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error_code", length = 128)
    private String lastErrorCode;

    @Column(name = "last_error_hash", length = 64)
    private String lastErrorHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IndexTaskEntity() {
    }

    public IndexTaskEntity(String noteId, TaskOperation operation, String expectedHash, String payloadJson, Instant now) {
        this.noteId = noteId;
        this.operation = operation;
        this.expectedHash = expectedHash;
        this.payloadJson = payloadJson;
        this.status = TaskStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void requeue(TaskOperation operation, String expectedHash, String payloadJson, Instant now) {
        this.operation = operation;
        this.expectedHash = expectedHash;
        this.payloadJson = payloadJson;
        this.status = TaskStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.lastErrorCode = null;
        this.lastErrorHash = null;
        this.updatedAt = now;
    }

    public void markProcessing(Instant now) {
        this.status = TaskStatus.PROCESSING;
        this.updatedAt = now;
    }

    public void markSucceeded(Instant now) {
        this.status = TaskStatus.SUCCEEDED;
        this.payloadJson = null;
        this.lastErrorCode = null;
        this.lastErrorHash = null;
        this.updatedAt = now;
    }

    public void markRetry(String errorCode, String errorHash, Instant retryAt, Instant now) {
        this.status = TaskStatus.PENDING;
        this.attempts++;
        this.lastErrorCode = errorCode;
        this.lastErrorHash = errorHash;
        this.nextAttemptAt = retryAt;
        this.updatedAt = now;
    }

    public void markDead(String errorCode, String errorHash, Instant now) {
        this.status = TaskStatus.DEAD;
        this.attempts++;
        this.lastErrorCode = errorCode;
        this.lastErrorHash = errorHash;
        this.updatedAt = now;
    }

    public String getNoteId() {
        return noteId;
    }

    public TaskOperation getOperation() {
        return operation;
    }

    public String getExpectedHash() {
        return expectedHash;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }
}
