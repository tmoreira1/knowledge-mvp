package com.knowledge.repository.dto;

import com.knowledge.repository.domain.Document;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public class DocumentDtos {

    public record CreateDocumentRequest(
            @NotBlank String title,
            String content,
            String slug,
            UUID projectId,
            UUID microserviceId,
            String domain,
            String category,
            List<String> tags,
            String owner) {
    }

    public record UpdateDocumentRequest(
            @NotBlank String title,
            String content,
            String slug,
            String domain,
            String category,
            List<String> tags,
            String owner) {
    }

    /**
     * Contract expected by knowledge-search: id, title, content, slug,
     * projectId, domain, tags (array). Extra fields are additive and safe.
     */
    public record DocumentResponse(
            UUID id,
            String title,
            String content,
            String slug,
            UUID projectId,
            UUID microserviceId,
            String domain,
            String category,
            List<String> tags,
            int currentVersion,
            String status,
            String owner) {

        public static DocumentResponse from(Document d) {
            String[] t = d.getTags();
            return new DocumentResponse(
                    d.getId(),
                    d.getTitle(),
                    d.getContent(),
                    d.getSlug(),
                    d.getProjectId(),
                    d.getMicroserviceId(),
                    d.getDomain(),
                    d.getCategory(),
                    t == null ? List.of() : List.of(t),
                    d.getCurrentVersion(),
                    d.getStatus(),
                    d.getOwner());
        }
    }

    public record DocumentVersionResponse(
            UUID id,
            UUID documentId,
            int version,
            String title,
            String content,
            String actor) {
    }
}
