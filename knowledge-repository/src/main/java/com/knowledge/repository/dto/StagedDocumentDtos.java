package com.knowledge.repository.dto;

import com.knowledge.repository.domain.StagedDocument;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public class StagedDocumentDtos {

    public record CreateStagedRequest(
            @NotBlank String rawContent,
            @NotBlank String sourceType,
            String externalId,
            String externalUrl,
            String suggestedTitle,
            String suggestedSlug,
            String suggestedCategory,
            UUID suggestedProjectId,
            String suggestedDomain,
            List<String> suggestedTags,
            String summary,
            UUID duplicateOf) {
    }

    public record RejectRequest(String reviewNote) {
    }

    public record StagedResponse(
            UUID id,
            String rawContent,
            String sourceType,
            String externalId,
            String externalUrl,
            String suggestedTitle,
            String suggestedSlug,
            String suggestedCategory,
            UUID suggestedProjectId,
            String suggestedDomain,
            List<String> suggestedTags,
            String summary,
            String status,
            UUID duplicateOf,
            UUID promotedDocumentId,
            String reviewNote) {

        public static StagedResponse from(StagedDocument s) {
            String[] t = s.getSuggestedTags();
            return new StagedResponse(
                    s.getId(),
                    s.getRawContent(),
                    s.getSourceType(),
                    s.getExternalId(),
                    s.getExternalUrl(),
                    s.getSuggestedTitle(),
                    s.getSuggestedSlug(),
                    s.getSuggestedCategory(),
                    s.getSuggestedProjectId(),
                    s.getSuggestedDomain(),
                    t == null ? List.of() : List.of(t),
                    s.getSummary(),
                    s.getStatus(),
                    s.getDuplicateOf(),
                    s.getPromotedDocumentId(),
                    s.getReviewNote());
        }
    }
}
