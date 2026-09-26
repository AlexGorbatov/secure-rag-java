package com.altronixsoft.securerag;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Chat model for tests: never calls a paid API, records every prompt so tests can assert what was sent,
 * and can be told to reply with a given text, a given token usage, or to fail.
 */
public class StubChatModel implements ChatModel {

    public static final String DEFAULT_REPLY = "stub answer";
    private static final DefaultUsage DEFAULT_USAGE = new DefaultUsage(10, 4);

    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private volatile String reply = DEFAULT_REPLY;
    private volatile DefaultUsage usage = DEFAULT_USAGE;
    private volatile RuntimeException failure;
    private volatile boolean replyWithNoGenerations = false;

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        if (failure != null) {
            throw failure;
        }
        ChatResponseMetadata metadata = ChatResponseMetadata.builder().usage(usage).build();
        if (replyWithNoGenerations) {
            return new ChatResponse(List.of(), metadata);
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))), metadata);
    }

    public List<Prompt> prompts() {
        return List.copyOf(prompts);
    }

    /** Everything sent to the model so far, system and user messages, as one string. */
    public String allPromptText() {
        return String.join("\n", prompts.stream().map(Prompt::getContents).toList());
    }

    public void replyWith(String text) {
        this.reply = text;
    }

    public void replyWithUsage(int promptTokens, int completionTokens) {
        this.usage = new DefaultUsage(promptTokens, completionTokens);
    }

    public void failWith(RuntimeException exception) {
        this.failure = exception;
    }

    /** Simulates a provider response with no generations at all (empty result, e.g. a content-filter refusal). */
    public void replyWithNoGenerations() {
        this.replyWithNoGenerations = true;
    }

    public void reset() {
        prompts.clear();
        reply = DEFAULT_REPLY;
        usage = DEFAULT_USAGE;
        failure = null;
        replyWithNoGenerations = false;
    }

}
