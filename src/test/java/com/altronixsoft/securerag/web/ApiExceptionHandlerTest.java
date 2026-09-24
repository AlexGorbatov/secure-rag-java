package com.altronixsoft.securerag.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.UUID;

import com.altronixsoft.securerag.service.exception.DocumentNotFoundException;
import com.altronixsoft.securerag.service.exception.DocumentTooLargeException;
import com.altronixsoft.securerag.service.exception.GroupNotAllowedException;
import com.altronixsoft.securerag.service.exception.IngestionFailedException;
import com.altronixsoft.securerag.service.exception.UnreadableDocumentException;
import com.altronixsoft.securerag.service.exception.UnsupportedDocumentException;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class ApiExceptionHandlerTest {

    static final UUID DOCUMENT_ID = UUID.fromString("7b1c3a52-0d6e-4f4b-9b0a-3c2f0f2b8e11");

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void documentNotFoundIs404WithoutRevealingTheId() throws Exception {
        String body = mvc.perform(get("/throw/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Document not found"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(DOCUMENT_ID.toString());
    }

    @Test
    void unsupportedDocumentIs415() throws Exception {
        mvc.perform(get("/throw/unsupported"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("Unsupported document type. Upload PDF, DOCX, Markdown or plain text."));
    }

    @Test
    void unreadableDocumentIs422AndHidesParserMessage() throws Exception {
        String body = mvc.perform(get("/throw/unreadable"))
                .andExpect(status().isUnprocessableContent())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("PDFBox");
    }

    @Test
    void tooMuchTextIs413() throws Exception {
        mvc.perform(get("/throw/too-much-text"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.detail").value("The document contains more text than allowed"));
    }

    @Test
    void sharingWithForeignGroupIs403() throws Exception {
        mvc.perform(get("/throw/group"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("A document can only be shared with groups you belong to"));
    }

    @Test
    void ingestionFailureIs422AndHidesTheCause() throws Exception {
        String body = mvc.perform(get("/throw/ingestion"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.documentId").value(DOCUMENT_ID.toString()))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Salary bands");
    }

    @Test
    void unexpectedExceptionIs500AndHidesTheMessage() throws Exception {
        String body = mvc.perform(get("/throw/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Internal error"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("SELECT");
    }

    @Test
    void uploadTooLargeIs413() throws Exception {
        mvc.perform(get("/throw/too-large"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void validationErrorIs400WithFieldErrors() throws Exception {
        mvc.perform(post("/throw/validate").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("question"));
    }

    @Test
    void classLevelValidationErrorIsReportedUnderTheObjectName() throws Exception {
        mvc.perform(post("/throw/validate-range").contentType(MediaType.APPLICATION_JSON).content("{\"from\":5,\"to\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("range"))
                .andExpect(jsonPath("$.errors[0].message").value("from must not be after to"));
    }

    @Test
    void clientDisconnectIsNotAServerError() throws Exception {
        mvc.perform(get("/throw/disconnected"))
                .andExpect(content().string(""));
    }

    @Test
    void springMvcErrorsKeepTheirOwnStatus() throws Exception {
        mvc.perform(post("/throw/not-found"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void accessDeniedIsLeftToSpringSecurity() {
        assertThatThrownBy(() -> mvc.perform(get("/throw/access-denied")))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void authenticationFailureIsLeftToSpringSecurity() {
        assertThatThrownBy(() -> mvc.perform(get("/throw/unauthenticated")))
                .hasRootCauseInstanceOf(BadCredentialsException.class);
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/throw/not-found")
        void notFound() {
            throw new DocumentNotFoundException(DOCUMENT_ID);
        }

        @GetMapping("/throw/unsupported")
        void unsupported() {
            throw new UnsupportedDocumentException("image/png");
        }

        @GetMapping("/throw/unreadable")
        void unreadable() {
            throw new UnreadableDocumentException("Parser rejected the file",
                    new IllegalStateException("PDFBox: missing root object"));
        }

        @GetMapping("/throw/too-much-text")
        void tooMuchText() {
            throw new DocumentTooLargeException(2_000_000, new IllegalStateException("write limit"));
        }

        @GetMapping("/throw/group")
        void group() {
            throw new GroupNotAllowedException("engineering");
        }

        @GetMapping("/throw/ingestion")
        void ingestion() {
            throw new IngestionFailedException(DOCUMENT_ID,
                    new IllegalStateException("Salary bands for 2026: engineers earn ..."));
        }

        @GetMapping("/throw/unexpected")
        void unexpected() {
            throw new IllegalStateException("SELECT * FROM documents WHERE owner_sub = 'alice'");
        }

        @GetMapping("/throw/too-large")
        void tooLarge() {
            throw new MaxUploadSizeExceededException(10 * 1024 * 1024);
        }

        @GetMapping("/throw/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/throw/unauthenticated")
        void unauthenticated() {
            throw new BadCredentialsException("bad token");
        }

        @GetMapping("/throw/disconnected")
        void disconnected() throws IOException {
            throw new IOException("Broken pipe");
        }

        @PostMapping("/throw/validate")
        void validate(@Valid @RequestBody Question question) {
        }

        @PostMapping("/throw/validate-range")
        void validateRange(@Valid @RequestBody Range range) {
        }

        record Question(@NotBlank String question) {
        }

        @FromNotAfterTo
        record Range(int from, int to) {
        }

    }

    /** A class-level constraint: it has no field, so it shows up as an object error. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = FromNotAfterToValidator.class)
    @interface FromNotAfterTo {
        String message() default "from must not be after to";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class FromNotAfterToValidator implements ConstraintValidator<FromNotAfterTo, ThrowingController.Range> {
        @Override
        public boolean isValid(ThrowingController.Range range, ConstraintValidatorContext context) {
            return range.from() <= range.to();
        }

    }

}
