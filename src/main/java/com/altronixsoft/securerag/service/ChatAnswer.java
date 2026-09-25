package com.altronixsoft.securerag.service;

import java.util.List;
import java.util.UUID;

/**
 * An answer and the documents it was built from.
 *
 * @param answer    the model's text, or a fixed message when nothing relevant was found
 * @param citations documents of the retrieved chunks, in retrieval order, each once; never taken from
 *                  the model's text
 */
public record ChatAnswer(String answer, List<Citation> citations) {

    public record Citation(UUID documentId, String title) {
    }

}
