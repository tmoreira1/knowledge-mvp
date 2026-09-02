package com.knowledge.repository.dto;

import com.knowledge.repository.domain.Microservice;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class MicroserviceDtos {

    public record CreateMicroserviceRequest(
            @NotNull UUID projectId,
            UUID productId,
            @NotBlank String name,
            String description,
            String status) {
    }

    public record UpdateMicroserviceRequest(
            @NotBlank String name,
            String description,
            String status) {
    }

    public record MicroserviceResponse(
            UUID id,
            UUID projectId,
            UUID productId,
            String name,
            String description,
            String status) {

        public static MicroserviceResponse from(Microservice m) {
            return new MicroserviceResponse(m.getId(), m.getProjectId(), m.getProductId(),
                    m.getName(), m.getDescription(), m.getStatus());
        }
    }
}
