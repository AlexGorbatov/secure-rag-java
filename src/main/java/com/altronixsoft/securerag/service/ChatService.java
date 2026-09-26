package com.altronixsoft.securerag.service;

import com.altronixsoft.securerag.model.Entitlements;
import com.altronixsoft.securerag.service.exception.AnswerGenerationFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
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

    private static final Usage NO_USAGE = new Usage(0, 0);

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
            return new ChatAnswer(NO_CONTEXT_ANSWER, List.of(), false, NO_USAGE);
        }
        Generated generated = generate(promptBuilder.build(question, chunks));
        log.info("Answered a question from {} chunks", chunks.size());
        return new ChatAnswer(generated.text(), citationsOf(chunks), true, usageOf(generated.response()));
    }

    private Generated generate(PromptBuilder.Prompt prompt) {
        ChatResponse response;
        try {
            response = chatClient.prompt()
                    .system(prompt.system())
                    .user(prompt.user())
                    .call()
                    .chatResponse();
        } catch (RuntimeException e) {
            throw new AnswerGenerationFailedException("Chat model call failed", e);
        }
        Generation result = response == null ? null : response.getResult();
        String text = result == null ? null : result.getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new AnswerGenerationFailedException("Chat model returned no text", null);
        }
        return new Generated(response, text);
    }

    private record Generated(ChatResponse response, String text) {
    }

    private static Usage usageOf(ChatResponse response) {
        org.springframework.ai.chat.metadata.Usage usage = response.getMetadata().getUsage();
        return new Usage(orZero(usage.getPromptTokens()), orZero(usage.getCompletionTokens()));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
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
