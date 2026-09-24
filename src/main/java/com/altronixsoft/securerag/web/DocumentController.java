package com.altronixsoft.securerag.web;

import com.altronixsoft.securerag.model.DocumentEntity;
import com.altronixsoft.securerag.model.Entitlements;
import com.altronixsoft.securerag.service.DocumentIngestionService;
import com.altronixsoft.securerag.service.DocumentQueryService;
import com.altronixsoft.securerag.service.DocumentService;
import com.altronixsoft.securerag.service.EntitlementsResolver;
import com.altronixsoft.securerag.web.dto.DocumentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents", description = "Upload documents; each is visible only to its owner and the groups it is shared with")
class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final DocumentQueryService queryService;
    private final DocumentService documentService;
    private final EntitlementsResolver entitlementsResolver;

    DocumentController(DocumentIngestionService ingestionService, DocumentQueryService queryService,
                       DocumentService documentService, EntitlementsResolver entitlementsResolver) {
        this.ingestionService = ingestionService;
        this.queryService = queryService;
        this.documentService = documentService;
        this.entitlementsResolver = entitlementsResolver;
    }

    @GetMapping
    @Operation(summary = "List documents", description = "Own documents plus those shared with any of the caller's groups, newest first.")
    @ApiResponse(responseCode = "200", description = "Visible documents")
    @ApiResponse(responseCode = "401", description = "Missing, expired or invalid access token")
    List<DocumentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return queryService.listVisible(caller(jwt)).stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a document", description = "404 both when it does not exist and when the caller may not see it.")
    @ApiResponse(responseCode = "200", description = "The document")
    @ApiResponse(responseCode = "401", description = "Missing, expired or invalid access token")
    @ApiResponse(responseCode = "404", description = "Not found or not visible to the caller")
    DocumentResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return DocumentResponse.from(queryService.getVisible(id, caller(jwt)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a document", description = "Only the owner can delete. Removes the document and all its chunks.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "401", description = "Missing, expired or invalid access token")
    @ApiResponse(responseCode = "404", description = "Not found, or the caller is not the owner")
    void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        documentService.delete(id, caller(jwt));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a document",
            description = "Parses the file, splits it into chunks and indexes them. The caller becomes the owner; "
                    + "the document can be shared only with groups the caller belongs to.")
    @ApiResponse(responseCode = "201", description = "Indexed; Location points to the document")
    @ApiResponse(responseCode = "401", description = "Missing, expired or invalid access token")
    @ApiResponse(responseCode = "403", description = "Sharing with a group the caller does not belong to")
    @ApiResponse(responseCode = "413", description = "File or extracted text is too large")
    @ApiResponse(responseCode = "415", description = "File type is not PDF, DOCX, Markdown or plain text")
    @ApiResponse(responseCode = "422", description = "No readable text, or indexing failed")
    ResponseEntity<DocumentResponse> upload(
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Groups to share the document with; omit to keep it private")
            @RequestParam(name = "groups", required = false) Set<String> groups,
            @AuthenticationPrincipal Jwt jwt) {
        DocumentEntity document = ingestionService.ingest(file, groups == null ? Set.of() : groups, caller(jwt));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(document.getId())
                .toUri();
        return ResponseEntity.created(location).body(DocumentResponse.from(document));
    }

    private Entitlements caller(Jwt jwt) {
        return entitlementsResolver.resolve(jwt);
    }

}
