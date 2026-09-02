package com.knowledge.repository.dto;

import com.knowledge.repository.domain.Project;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class ProjectDtos {

    public record CreateProjectRequest(
            @NotBlank String name,
            String description,
            String status) {
    }

    public record UpdateProjectRequest(
            @NotBlank String name,
            String description,
            String status) {
    }

    public record ProjectResponse(
            UUID id,
            String name,
            String description,
            String status) {

        public static ProjectResponse from(Project p) {
            return new ProjectResponse(p.getId(), p.getName(), p.getDescription(), p.getStatus());
        }
    }
}
