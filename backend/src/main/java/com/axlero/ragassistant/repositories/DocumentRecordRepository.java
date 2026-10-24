
package com.axlero.ragassistant.repositories;

import com.axlero.ragassistant.entities.DocumentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRecordRepository extends JpaRepository<DocumentRecord, UUID> {

    List<DocumentRecord> findAllByOrderByUploadedAtDesc();

    List<DocumentRecord> findAllByUploadedByOrderByUploadedAtDesc(UUID uploadedBy);

    Optional<DocumentRecord> findByIdAndUploadedBy(UUID id, UUID uploadedBy);

    List<DocumentRecord> findAllByOrderByUploadedAtDesc();
}
