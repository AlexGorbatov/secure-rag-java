package com.altronixsoft.securerag.web.dto;

import com.altronixsoft.securerag.service.ChatAnswer;

import java.util.List;
import java.util.UUID;

/**
 * Named {@code AnswerResponse} to avoid clashing with Spring AI's {@code ChatResponse}.
 */
public record AnswerResponse(String answer, List<CitationResponse> citations, boolean grounded, UsageResponse usage) {

    public static AnswerResponse from(ChatAnswer answer) {
        return new AnswerResponse(
                answer.answer(),
                answer.citations().stream()
                        .map(citation -> new CitationResponse(citation.documentId(), citation.title()))
                        .toList(),
                answer.grounded(),
                new UsageResponse(answer.usage().promptTokens(), answer.usage().completionTokens()));
    }

    public record CitationResponse(UUID documentId, String title) {
    }

    public record UsageResponse(int promptTokens, int completionTokens) {
    }

}
