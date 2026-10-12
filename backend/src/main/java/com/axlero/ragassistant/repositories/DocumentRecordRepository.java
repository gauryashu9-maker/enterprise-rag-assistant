package com.axlero.ragassistant.repositories;

import com.axlero.ragassistant.entities.DocumentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentRecordRepository extends JpaRepository<DocumentRecord, UUID> {
}
