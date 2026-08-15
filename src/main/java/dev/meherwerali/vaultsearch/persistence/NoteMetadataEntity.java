package dev.meherwerali.vaultsearch.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notes")
public class NoteMetadataEntity {

    @Id
    @Column(length = 64)
    private String id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "vault_id", nullable = false, length = 36)
    private UUID vaultId;

    @Column(name = "relative_path", nullable = false, length = 1024)
    private String relativePath;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "projection_hash", nullable = false, length = 64)
    private String projectionHash;

    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "index_state", nullable = false, length = 32)
    private IndexState indexState;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NoteMetadataEntity() {
    }

    public NoteMetadataEntity(String id, UUID vaultId, String relativePath, String title, String sourceHash,
                              String projectionHash, Instant modifiedAt, Instant now) {
        this.id = id;
        this.vaultId = vaultId;
        this.relativePath = relativePath;
        this.title = title;
        this.sourceHash = sourceHash;
        this.projectionHash = projectionHash;
        this.modifiedAt = modifiedAt;
        this.indexState = IndexState.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void refresh(String title, String sourceHash, String projectionHash, Instant modifiedAt, Instant now) {
        this.title = title;
        this.sourceHash = sourceHash;
        this.projectionHash = projectionHash;
        this.modifiedAt = modifiedAt;
        this.indexState = IndexState.PENDING;
        this.deleted = false;
        this.updatedAt = now;
    }

    public void markDeleted(Instant now) {
        this.deleted = true;
        this.indexState = IndexState.PENDING;
        this.updatedAt = now;
    }

    public void markIndexed(Instant now) {
        this.indexState = IndexState.INDEXED;
        this.deleted = false;
        this.updatedAt = now;
    }

    public void markDeleteProjected(Instant now) {
        this.indexState = IndexState.DELETED;
        this.deleted = true;
        this.updatedAt = now;
    }

    public void markFailed(Instant now) {
        this.indexState = IndexState.FAILED;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public UUID getVaultId() {
        return vaultId;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getTitle() {
        return title;
    }

    public String getProjectionHash() {
        return projectionHash;
    }

    public IndexState getIndexState() {
        return indexState;
    }

    public boolean isDeleted() {
        return deleted;
    }
}
