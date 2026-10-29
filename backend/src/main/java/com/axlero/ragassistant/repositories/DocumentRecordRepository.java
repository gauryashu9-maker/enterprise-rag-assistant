package com.axlero.ragassistant.repositories;

import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRecordRepository
        extends JpaRepository<DocumentRecord, UUID> {

    List<DocumentRecord> findAllByUploadedByOrderByUploadedAtDesc(
            UUID uploadedBy);

    Optional<DocumentRecord> findByIdAndUploadedBy(
            UUID id, UUID uploadedBy);

    List<DocumentRecord> findAllByOrderByUploadedAtDesc();

    List<DocumentRecord>
            findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                    List<DocumentStatus> statuses, Instant now);

    List<DocumentRecord>
            findTop20ByStatusAndNextRetryAtIsNullOrderByUploadedAtAsc(
                    DocumentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DocumentRecord d where d.id = :id")
    Optional<DocumentRecord> findByIdForUpdate(
            @Param("id") UUID id);
}
