package com.altronixsoft.securerag.service;

/**
 * Token usage for one chat model call. Zero when no model call was made (e.g. no permitted context).
 */
public record Usage(int promptTokens, int completionTokens) {
}
