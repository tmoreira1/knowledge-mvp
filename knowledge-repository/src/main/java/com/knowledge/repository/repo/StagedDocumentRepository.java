package com.knowledge.repository.repo;

import com.knowledge.repository.domain.StagedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StagedDocumentRepository extends JpaRepository<StagedDocument, UUID> {
    Optional<StagedDocument> findBySourceTypeAndExternalId(String sourceType, String externalId);
}
