package com.altronixsoft.securerag.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A question for the assistant. Only the text: what the caller may read comes from the token.
 */
public record ChatQuestion(@NotBlank @Size(max = MAX_QUESTION_LENGTH) String question) {

    public static final int MAX_QUESTION_LENGTH = 2000;

}
