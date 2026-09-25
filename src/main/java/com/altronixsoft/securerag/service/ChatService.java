package com.altronixsoft.securerag.service;

import com.altronixsoft.securerag.model.Entitlements;
import com.altronixsoft.securerag.service.exception.AnswerGenerationFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Question → permitted chunks → prompt → model → answer with citations.
 *
 * <p>Access is decided before the model runs: {@link RetrievalService} returns only chunks the caller
 * may read, so nothing the model does can widen it. Citations come from those chunks, never from what
 * the model wrote. Not transactional: the model call can take seconds.
 */
@Slf4j
@Service
public class ChatService {

    static final String NO_CONTEXT_ANSWER =
            "I could not find information about this in the documents available to you.";

    private final RetrievalService retrievalService;
    private final PromptBuilder promptBuilder;
    private final ChatClient chatClient;

    ChatService(RetrievalService retrievalService, PromptBuilder promptBuilder, ChatClient chatClient) {
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClient;
    }

    public ChatAnswer answer(String question, Entitlements caller) {
        List<Document> chunks = retrievalService.findRelevant(question, caller);
        if (chunks.isEmpty()) {
            // No model call: without context it would answer from general knowledge.
            return new ChatAnswer(NO_CONTEXT_ANSWER, List.of());
        }
        String answer = generate(promptBuilder.build(question, chunks));
        log.info("Answered a question from {} chunks", chunks.size());
        return new ChatAnswer(answer, citationsOf(chunks));
    }

    private String generate(PromptBuilder.Prompt prompt) {
        String answer;
        try {
            answer = chatClient.prompt()
                    .system(prompt.system())
                    .user(prompt.user())
                    .call()
                    .content();
        } catch (RuntimeException e) {
            throw new AnswerGenerationFailedException("Chat model call failed", e);
        }
        if (answer == null || answer.isBlank()) {
            throw new AnswerGenerationFailedException("Chat model returned no text", null);
        }
        return answer;
    }

    /** One citation per document, in the order its first chunk was retrieved. */
    private static List<ChatAnswer.Citation> citationsOf(List<Document> chunks) {
        Map<UUID, ChatAnswer.Citation> byDocument = new LinkedHashMap<>();
        for (Document chunk : chunks) {
            UUID documentId = UUID.fromString((String) chunk.getMetadata().get(ChunkMetadata.DOCUMENT_ID));
            String title = (String) chunk.getMetadata().get(ChunkMetadata.TITLE);
            byDocument.putIfAbsent(documentId, new ChatAnswer.Citation(documentId, title));
        }
        return List.copyOf(byDocument.values());
    }

}
