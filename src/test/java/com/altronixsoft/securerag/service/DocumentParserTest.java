package com.altronixsoft.securerag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import com.altronixsoft.securerag.config.IngestionProperties;
import com.altronixsoft.securerag.service.exception.DocumentTooLargeException;
import com.altronixsoft.securerag.service.exception.UnreadableDocumentException;
import com.altronixsoft.securerag.service.exception.UnsupportedDocumentException;
import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentParserTest {

    private static final int MAX_TEXT_CHARACTERS = 200;
    private static final int MAX_TITLE_LENGTH = 20;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};

    private final DocumentParser parser = new DocumentParser(
            new Tika(), new IngestionProperties(100, MAX_TEXT_CHARACTERS, MAX_TITLE_LENGTH));

    @Test
    void parsesPlainText() {
        ParsedDocument parsed = parser.parse(file("policy.txt", "Vacation policy: 25 days of paid leave."));

        assertThat(parsed.contentType()).isEqualTo("text/plain");
        assertThat(parsed.title()).isEqualTo("policy.txt");
        assertThat(parsed.text()).contains("25 days of paid leave");
        assertThat(parsed.sizeBytes()).isPositive();
    }

    @Test
    void acceptsMarkdown() {
        ParsedDocument parsed = parser.parse(file("notes.md", "# Notes\n\nOffice opens at nine."));

        assertThat(DocumentParser.SUPPORTED_TYPES).contains(parsed.contentType());
        assertThat(parsed.text()).contains("Office opens at nine");
    }

    @Test
    void detectsTypeFromContentNotFromFileName() {
        MockMultipartFile disguisedImage = new MockMultipartFile("file", "policy.txt", "text/plain", PNG_SIGNATURE);

        assertThatThrownBy(() -> parser.parse(disguisedImage))
                .isInstanceOf(UnsupportedDocumentException.class)
                .satisfies(e -> assertThat(((UnsupportedDocumentException) e).getDocumentType()).isEqualTo("image/png"));
    }

    @Test
    void rejectsFileWithoutText() {
        assertThatThrownBy(() -> parser.parse(file("empty.txt", "   \n  ")))
                .isInstanceOf(UnreadableDocumentException.class);
    }

    @Test
    void rejectsCorruptedPdf() {
        assertThatThrownBy(() -> parser.parse(file("broken.pdf", "%PDF-1.7\nthis is not a real pdf")))
                .isInstanceOf(UnreadableDocumentException.class);
    }

    @Test
    void stopsParsingWhenTextExceedsLimit() {
        String longText = "word ".repeat(MAX_TEXT_CHARACTERS);

        assertThatThrownBy(() -> parser.parse(file("long.txt", longText)))
                .isInstanceOf(DocumentTooLargeException.class);
    }

    @Test
    void titleDropsDirectoryPartsSentByBrowsers() {
        assertThat(parser.parse(file("C:\\fakepath\\report.txt", "text")).title()).isEqualTo("report.txt");
        assertThat(parser.parse(file("../../etc/report.txt", "text")).title()).isEqualTo("report.txt");
    }

    @Test
    void titleIsTrimmedToMaximumLength() {
        String title = parser.parse(file("a-very-long-file-name-for-a-report.txt", "text")).title();

        assertThat(title).hasSize(MAX_TITLE_LENGTH);
    }

    @Test
    void fileWithoutNameIsUntitled() {
        assertThat(parser.parse(file("", "text")).title()).isEqualTo(DocumentParser.UNTITLED);
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content.getBytes(StandardCharsets.UTF_8));
    }

}
