package com.altronixsoft.securerag.web;

import com.altronixsoft.securerag.model.Entitlements;
import com.altronixsoft.securerag.service.EntitlementsResolver;
import com.altronixsoft.securerag.service.RetrievalService;
import com.altronixsoft.securerag.web.dto.SearchHitResponse;
import com.altronixsoft.securerag.web.dto.SearchQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.ai.document.Document;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "Semantic search over the documents the caller may read")
class SearchController {

    private final RetrievalService retrievalService;
    private final EntitlementsResolver entitlementsResolver;

    SearchController(RetrievalService retrievalService, EntitlementsResolver entitlementsResolver) {
        this.retrievalService = retrievalService;
        this.entitlementsResolver = entitlementsResolver;
    }

    @PostMapping
    @Operation(summary = "Search documents",
            description = "Returns the chunks most similar in meaning to the query, from the caller's own documents "
                    + "and those shared with their groups only.")
    @ApiResponse(responseCode = "200", description = "Matching chunks, most similar first")
    @ApiResponse(responseCode = "400", description = "Blank or too long query, or topK out of range")
    @ApiResponse(responseCode = "401", description = "Missing, expired or invalid access token")
    List<SearchHitResponse> search(@Valid @RequestBody SearchQuery request, @AuthenticationPrincipal Jwt jwt) {
        Entitlements caller = entitlementsResolver.resolve(jwt);
        List<Document> chunks = request.topK() == null
                ? retrievalService.findRelevant(request.query(), caller)
                : retrievalService.findRelevant(request.query(), request.topK(), caller);
        return chunks.stream().map(SearchHitResponse::from).toList();
    }

}
