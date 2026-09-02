package com.knowledge.repository.web;

import com.knowledge.repository.dto.DocumentDtos.DocumentResponse;
import com.knowledge.repository.dto.StagedDocumentDtos.CreateStagedRequest;
import com.knowledge.repository.dto.StagedDocumentDtos.RejectRequest;
import com.knowledge.repository.dto.StagedDocumentDtos.StagedResponse;
import com.knowledge.repository.service.StagedDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/staged-documents")
@RequiredArgsConstructor
public class StagedDocumentController {

    private final StagedDocumentService service;

    @GetMapping
    public List<StagedResponse> list() {
        return service.findAll().stream().map(StagedResponse::from).toList();
    }

    @GetMapping("/{id}")
    public StagedResponse get(@PathVariable UUID id) {
        return StagedResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StagedResponse stage(@Valid @RequestBody CreateStagedRequest req) {
        return StagedResponse.from(service.stage(req));
    }

    @PostMapping("/{id}/approve")
    public DocumentResponse approve(@PathVariable UUID id) {
        return DocumentResponse.from(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    public StagedResponse reject(@PathVariable UUID id,
                                 @RequestBody(required = false) RejectRequest req) {
        return StagedResponse.from(service.reject(id, req == null ? null : req.reviewNote()));
    }
}
