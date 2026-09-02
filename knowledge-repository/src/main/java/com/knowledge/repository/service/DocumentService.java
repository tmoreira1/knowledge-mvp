package com.knowledge.repository.service;

import com.knowledge.repository.audit.ActorContext;
import com.knowledge.repository.audit.AuditService;
import com.knowledge.repository.domain.Document;
import com.knowledge.repository.domain.DocumentVersion;
import com.knowledge.repository.dto.DocumentDtos.CreateDocumentRequest;
import com.knowledge.repository.dto.DocumentDtos.UpdateDocumentRequest;
import com.knowledge.repository.event.DocumentEventPublisher;
import com.knowledge.repository.repo.DocumentRepository;
import com.knowledge.repository.repo.DocumentVersionRepository;
import com.knowledge.repository.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final AuditService audit;
    private final DocumentEventPublisher events;

    public List<Document> findAll() {
        return documentRepository.findAll();
    }

    public Document get(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Document not found: " + id));
    }

    public List<DocumentVersion> versions(UUID id) {
        get(id); // 404 if missing
        return versionRepository.findByDocumentIdOrderByVersionAsc(id);
    }

    private static String[] toArray(List<String> tags) {
        return tags == null ? new String[0] : tags.toArray(new String[0]);
    }

    /** Creates a document at version 1 and its first version row. */
    @Transactional
    public Document create(CreateDocumentRequest req) {
        Document d = new Document();
        d.setTitle(req.title());
        d.setContent(req.content() == null ? "" : req.content());
        d.setSlug(req.slug());
        d.setProjectId(req.projectId());
        d.setMicroserviceId(req.microserviceId());
        d.setDomain(req.domain());
        d.setCategory(req.category());
        d.setTags(toArray(req.tags()));
        d.setOwner(req.owner());
        d.setCurrentVersion(1);
        d = documentRepository.save(d);

        writeVersion(d);
        audit.record("Document", d.getId(), "CREATE", Map.of("title", d.getTitle(), "version", 1));
        events.publishCreated(d.getId());
        return d;
    }

    /** Updates the living content and appends a new version row. */
    @Transactional
    public Document update(UUID id, UpdateDocumentRequest req) {
        Document d = get(id);
        d.setTitle(req.title());
        if (req.content() != null) {
            d.setContent(req.content());
        }
        d.setSlug(req.slug());
        d.setDomain(req.domain());
        d.setCategory(req.category());
        if (req.tags() != null) {
            d.setTags(toArray(req.tags()));
        }
        d.setOwner(req.owner());
        d.setCurrentVersion(d.getCurrentVersion() + 1);
        d = documentRepository.save(d);

        writeVersion(d);
        audit.record("Document", d.getId(), "UPDATE",
                Map.of("title", d.getTitle(), "version", d.getCurrentVersion()));
        events.publishUpdated(d.getId());
        return d;
    }

    @Transactional
    public void delete(UUID id) {
        Document d = get(id);
        documentRepository.delete(d);
        audit.record("Document", id, "DELETE", Map.of());
        events.publishDeleted(id);
    }

    /**
     * Appends an immutable version row mirroring the document's current
     * title/content at its current_version. Called within the enclosing
     * transaction so create/update stays atomic.
     */
    private void writeVersion(Document d) {
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(d.getId());
        v.setVersion(d.getCurrentVersion());
        v.setTitle(d.getTitle());
        v.setContent(d.getContent());
        v.setActor(ActorContext.get());
        versionRepository.save(v);
    }
}
