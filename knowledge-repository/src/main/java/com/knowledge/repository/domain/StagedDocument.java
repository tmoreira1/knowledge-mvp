package com.knowledge.repository.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "staged_document")
public class StagedDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "raw_content", nullable = false)
    private String rawContent = "";

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "external_url")
    private String externalUrl;

    @Column(name = "suggested_title")
    private String suggestedTitle;

    @Column(name = "suggested_slug")
    private String suggestedSlug;

    @Column(name = "suggested_category")
    private String suggestedCategory;

    @Column(name = "suggested_project_id")
    private UUID suggestedProjectId;

    @Column(name = "suggested_domain")
    private String suggestedDomain;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "suggested_tags", columnDefinition = "text[]")
    private String[] suggestedTags = new String[0];

    private String summary;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "duplicate_of")
    private UUID duplicateOf;

    @Column(name = "promoted_document_id")
    private UUID promotedDocumentId;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
