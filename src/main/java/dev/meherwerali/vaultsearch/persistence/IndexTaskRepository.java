package dev.meherwerali.vaultsearch.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndexTaskRepository extends JpaRepository<IndexTaskEntity, String> {

    List<IndexTaskEntity> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<TaskStatus> statuses, Instant now);

    long countByStatus(TaskStatus status);

    long countByStatusIn(Collection<TaskStatus> statuses);
}
