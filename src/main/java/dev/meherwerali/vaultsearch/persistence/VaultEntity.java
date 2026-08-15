package dev.meherwerali.vaultsearch.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "vaults")
public class VaultEntity {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 36)
    private UUID id;

    @Column(name = "vault_key", nullable = false, unique = true, length = 128)
    private String vaultKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "root_fingerprint", nullable = false, length = 64)
    private String rootFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VaultEntity() {
    }

    public VaultEntity(UUID id, String vaultKey, String displayName, String rootFingerprint, Instant now) {
        this.id = id;
        this.vaultKey = vaultKey;
        this.displayName = displayName;
        this.rootFingerprint = rootFingerprint;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void refresh(String displayName, String rootFingerprint, Instant now) {
        this.displayName = displayName;
        this.rootFingerprint = rootFingerprint;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }
}
