package com.altronixsoft.securerag.service;

/**
 * An uploaded file after validation and text extraction, before anything is stored.
 *
 * @param title       display name derived from the file name; never used as a path
 * @param contentType type detected from the file's bytes, not the one the client declared
 * @param sizeBytes   size of the uploaded file
 * @param text        extracted text, non-blank and within the configured limit
 */
public record ParsedDocument(String title, String contentType, long sizeBytes, String text) {
}
