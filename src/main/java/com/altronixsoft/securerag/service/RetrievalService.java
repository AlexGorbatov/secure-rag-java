package com.altronixsoft.securerag.service;

import com.altronixsoft.securerag.config.RetrievalProperties;
import com.altronixsoft.securerag.model.Entitlements;
import com.altronixsoft.securerag.service.exception.SearchUnavailableException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Similarity search restricted to the chunks the caller may read. The access filter is part of the
 * {@link SearchRequest}, so pgvector applies it in the same query as the similarity ranking; results are
 * never trimmed afterwards.
 */
@Service
public class RetrievalService {

    private final VectorStore vectorStore;
    private final RetrievalProperties properties;

    RetrievalService(VectorStore vectorStore, RetrievalProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public List<Document> findRelevant(String query, Entitlements caller) {
        return findRelevant(query, properties.defaultTopK(), caller);
    }

    public List<Document> findRelevant(String query, int topK, Entitlements caller) {
        return AccessFilter.visibleTo(caller)
                .map(filter -> search(SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(properties.similarityThreshold())
                        .filterExpression(filter)
                        .build()))
                .orElseGet(List::of);
    }

    /** The query is embedded by a remote provider first, so an outage there surfaces here. */
    private List<Document> search(SearchRequest request) {
        try {
            return vectorStore.similaritySearch(request);
        } catch (RuntimeException e) {
            throw new SearchUnavailableException(e);
        }
    }

}
