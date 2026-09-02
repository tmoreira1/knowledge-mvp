package com.knowledge.repository.web;

import com.knowledge.repository.dto.DocumentDtos.CreateDocumentRequest;
import com.knowledge.repository.dto.DocumentDtos.DocumentResponse;
import com.knowledge.repository.dto.DocumentDtos.DocumentVersionResponse;
import com.knowledge.repository.dto.DocumentDtos.UpdateDocumentRequest;
import com.knowledge.repository.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService service;

    @GetMapping
    public List<DocumentResponse> list() {
        return service.findAll().stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return DocumentResponse.from(service.get(id));
    }

    @GetMapping("/{id}/versions")
    public List<DocumentVersionResponse> versions(@PathVariable UUID id) {
        return service.versions(id).stream()
                .map(v -> new DocumentVersionResponse(v.getId(), v.getDocumentId(),
                        v.getVersion(), v.getTitle(), v.getContent(), v.getActor()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse create(@Valid @RequestBody CreateDocumentRequest req) {
        return DocumentResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    public DocumentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest req) {
        return DocumentResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
