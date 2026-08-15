package dev.meherwerali.vaultsearch.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultRepository extends JpaRepository<VaultEntity, UUID> {

    Optional<VaultEntity> findByVaultKey(String vaultKey);
}
