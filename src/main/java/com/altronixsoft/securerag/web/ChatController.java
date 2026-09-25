package com.altronixsoft.securerag.web;

import com.altronixsoft.securerag.service.ChatService;
import com.altronixsoft.securerag.service.EntitlementsResolver;
import com.altronixsoft.securerag.web.dto.AnswerResponse;
import com.altronixsoft.securerag.web.dto.ChatQuestion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "Answers built only from the documents the caller may read")
class ChatController {

    private final ChatService chatService;
    private final EntitlementsResolver entitlementsResolver;

    ChatController(ChatService chatService, EntitlementsResolver entitlementsResolver) {
        this.chatService = chatService;
        this.entitlementsResolver = entitlementsResolver;
    }

    @PostMapping
    @Operation(summary = "Ask a question",
            description = "Finds the most relevant chunks among the caller's own and shared documents, asks the "
                    + "model to answer from them only, and returns the answer with the documents it came from.")
    @ApiResponse(responseCode = "200", description = "Answer and citations")
    @ApiResponse(responseCode = "400", description = "Blank or too long question")
    @ApiResponse(responseCode = "401", description = "Missing, expired or invalid access token")
    @ApiResponse(responseCode = "502", description = "The chat model could not produce an answer")
    AnswerResponse ask(@Valid @RequestBody ChatQuestion request, @AuthenticationPrincipal Jwt jwt) {
        return AnswerResponse.from(chatService.answer(request.question(), entitlementsResolver.resolve(jwt)));
    }

}
