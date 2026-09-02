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
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "microservice_id")
    private UUID microserviceId;

    @Column(name = "space_id")
    private UUID spaceId;

    @Column(nullable = false)
    private String title;

    private String slug;

    private String domain;

    private String category;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags = new String[0];

    @Column(nullable = false)
    private String content = "";

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;

    @Column(nullable = false)
    private String status = "ACTIVE";

    private String owner;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
