package dev.meherwerali.vaultsearch.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteMetadataRepository extends JpaRepository<NoteMetadataEntity, String> {

    List<NoteMetadataEntity> findAllByVaultId(UUID vaultId);

    long countByVaultIdAndDeletedFalse(UUID vaultId);

    long countByDeletedFalse();

    Optional<NoteMetadataEntity> findByIdAndDeletedFalse(String id);
}
