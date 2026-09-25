package com.altronixsoft.securerag;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Chat model for tests: never calls a paid API, records every prompt so tests can assert what was sent,
 * and can be told to reply with a given text or to fail.
 */
public class StubChatModel implements ChatModel {

    public static final String DEFAULT_REPLY = "stub answer";

    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private volatile String reply = DEFAULT_REPLY;
    private volatile RuntimeException failure;

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        if (failure != null) {
            throw failure;
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
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

    public void failWith(RuntimeException exception) {
        this.failure = exception;
    }

    public void reset() {
        prompts.clear();
        reply = DEFAULT_REPLY;
        failure = null;
    }

}
