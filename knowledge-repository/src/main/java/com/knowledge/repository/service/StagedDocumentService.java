package com.knowledge.repository.service;

import com.knowledge.repository.audit.ActorContext;
import com.knowledge.repository.audit.AuditService;
import com.knowledge.repository.domain.Document;
import com.knowledge.repository.domain.DocumentVersion;
import com.knowledge.repository.domain.StagedDocument;
import com.knowledge.repository.dto.StagedDocumentDtos.CreateStagedRequest;
import com.knowledge.repository.event.DocumentEventPublisher;
import com.knowledge.repository.repo.DocumentRepository;
import com.knowledge.repository.repo.DocumentVersionRepository;
import com.knowledge.repository.repo.StagedDocumentRepository;
import com.knowledge.repository.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StagedDocumentService {

    private final StagedDocumentRepository stagedRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final AuditService audit;
    private final DocumentEventPublisher events;

    public List<StagedDocument> findAll() {
        return stagedRepository.findAll();
    }

    public StagedDocument get(UUID id) {
        return stagedRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("StagedDocument not found: " + id));
    }

    private static String[] toArray(List<String> tags) {
        return tags == null ? new String[0] : tags.toArray(new String[0]);
    }

    /**
     * Idempotent per (source_type, external_id): if a staged row already
     * exists for that pair it is returned unchanged instead of duplicated.
     */
    @Transactional
    public StagedDocument stage(CreateStagedRequest req) {
        if (req.externalId() != null) {
            var existing = stagedRepository
                    .findBySourceTypeAndExternalId(req.sourceType(), req.externalId());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        StagedDocument s = new StagedDocument();
        s.setRawContent(req.rawContent());
        s.setSourceType(req.sourceType());
        s.setExternalId(req.externalId());
        s.setExternalUrl(req.externalUrl());
        s.setSuggestedTitle(req.suggestedTitle());
        s.setSuggestedSlug(req.suggestedSlug());
        s.setSuggestedCategory(req.suggestedCategory());
        s.setSuggestedProjectId(req.suggestedProjectId());
        s.setSuggestedDomain(req.suggestedDomain());
        s.setSuggestedTags(toArray(req.suggestedTags()));
        s.setSummary(req.summary());
        s.setDuplicateOf(req.duplicateOf());
        s.setStatus("PENDING");
        s = stagedRepository.save(s);
        audit.record("StagedDocument", s.getId(), "CREATE",
                Map.of("sourceType", s.getSourceType()));
        return s;
    }

    /**
     * Promotes a PENDING staged row to a real Document (version 1 + version
     * row), records auditing and fires DocumentCreated -- all in one
     * transaction. The staged row is marked APPROVED with promoted_document_id.
     */
    @Transactional
    public Document approve(UUID id) {
        StagedDocument s = get(id);
        if (!"PENDING".equals(s.getStatus())) {
            throw new IllegalStateException("StagedDocument is not PENDING: " + s.getStatus());
        }

        Document d = new Document();
        d.setTitle(s.getSuggestedTitle() != null ? s.getSuggestedTitle() : "Untitled");
        d.setContent(s.getRawContent() == null ? "" : s.getRawContent());
        d.setSlug(s.getSuggestedSlug());
        d.setProjectId(s.getSuggestedProjectId());
        d.setDomain(s.getSuggestedDomain());
        d.setCategory(s.getSuggestedCategory());
        d.setTags(s.getSuggestedTags());
        d.setCurrentVersion(1);
        d = documentRepository.save(d);

        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(d.getId());
        v.setVersion(1);
        v.setTitle(d.getTitle());
        v.setContent(d.getContent());
        v.setActor(ActorContext.get());
        versionRepository.save(v);

        s.setStatus("APPROVED");
        s.setPromotedDocumentId(d.getId());
        stagedRepository.save(s);

        audit.record("StagedDocument", s.getId(), "APPROVE",
                Map.of("promotedDocumentId", d.getId().toString()));
        audit.record("Document", d.getId(), "CREATE",
                Map.of("title", d.getTitle(), "version", 1, "fromStaged", s.getId().toString()));
        events.publishCreated(d.getId());
        return d;
    }

    @Transactional
    public StagedDocument reject(UUID id, String reviewNote) {
        StagedDocument s = get(id);
        if (!"PENDING".equals(s.getStatus())) {
            throw new IllegalStateException("StagedDocument is not PENDING: " + s.getStatus());
        }
        s.setStatus("REJECTED");
        s.setReviewNote(reviewNote);
        s = stagedRepository.save(s);
        audit.record("StagedDocument", s.getId(), "REJECT",
                Map.of("reviewNote", reviewNote == null ? "" : reviewNote));
        return s;
    }
}
