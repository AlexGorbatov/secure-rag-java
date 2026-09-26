package com.altronixsoft.securerag.service;

import java.util.List;
import java.util.UUID;

/**
 * An answer and the documents it was built from.
 *
 * @param answer    the model's text, or a fixed message when nothing relevant was found
 * @param citations documents of the retrieved chunks, in retrieval order, each once; never taken from
 *                  the model's text
 * @param grounded  {@code true} when the answer was built from at least one retrieved chunk
 * @param usage     token usage for the model call; zero when no model call was made
 */
public record ChatAnswer(String answer, List<Citation> citations, boolean grounded, Usage usage) {

    public record Citation(UUID documentId, String title) {
    }

}
