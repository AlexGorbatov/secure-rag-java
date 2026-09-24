package com.altronixsoft.securerag.service;

import com.altronixsoft.securerag.config.IngestionProperties;
import com.altronixsoft.securerag.service.exception.DocumentTooLargeException;
import com.altronixsoft.securerag.service.exception.UnreadableDocumentException;
import com.altronixsoft.securerag.service.exception.UnsupportedDocumentException;
import org.apache.tika.Tika;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns an untrusted upload into text. Nothing here touches the database or the vector store, so a
 * rejected file leaves no trace.
 */
@Service
public class DocumentParser {

    /**
     * Types detected from the file's bytes that may be uploaded. Tika reports Markdown files as
     * {@code text/x-web-markdown}.
     */
    static final Set<String> SUPPORTED_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/markdown",
            "text/x-web-markdown");

    static final String UNTITLED = "Untitled";

    private static final String PAGE_SEPARATOR = "\n\n";

    private final Tika tika;
    private final IngestionProperties properties;

    DocumentParser(Tika tika, IngestionProperties properties) {
        this.tika = tika;
        this.properties = properties;
    }

    public ParsedDocument parse(MultipartFile file) {
        String contentType = detectContentType(file);
        if (!SUPPORTED_TYPES.contains(contentType)) {
            throw new UnsupportedDocumentException(contentType);
        }
        String text = extractText(file);
        if (text.isBlank()) {
            throw new UnreadableDocumentException("No extractable text in a " + contentType + " file");
        }
        return new ParsedDocument(titleOf(file), contentType, file.getSize(), text);
    }

    private String detectContentType(MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return tika.detect(content, file.getOriginalFilename());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }

    private String extractText(MultipartFile file) {
        // The write limit stops parsing as soon as the text passes the cap, so a compression bomb
        // never gets fully expanded in memory.
        BodyContentHandler handler = new BodyContentHandler(properties.maxTextCharacters());
        try {
            return new TikaDocumentReader(file.getResource(), handler, ExtractedTextFormatter.defaults())
                    .get().stream()
                    .map(Document::getText)
                    .collect(Collectors.joining(PAGE_SEPARATOR));
        } catch (RuntimeException e) {
            if (WriteLimitReachedException.isWriteLimitReached(e)) {
                throw new DocumentTooLargeException(properties.maxTextCharacters(), e);
            }
            throw new UnreadableDocumentException("Parser rejected the file", e);
        }
    }

    /**
     * The file name without any directory part (some browsers send {@code C:\fakepath\name.pdf}),
     * trimmed to the configured length. It is display text only and never becomes a path.
     */
    private String titleOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) {
            return UNTITLED;
        }
        String baseName = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).strip();
        if (baseName.isEmpty()) {
            return UNTITLED;
        }
        return baseName.length() <= properties.maxTitleLength()
                ? baseName
                : baseName.substring(0, properties.maxTitleLength());
    }

}
