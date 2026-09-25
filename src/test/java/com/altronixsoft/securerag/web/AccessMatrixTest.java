package com.altronixsoft.securerag.web;

import static com.altronixsoft.securerag.service.IngestionTestSupport.textFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.altronixsoft.securerag.StubChatModel;
import com.altronixsoft.securerag.TestcontainersConfiguration;
import com.altronixsoft.securerag.repository.DocumentRepository;
import com.altronixsoft.securerag.service.IngestionTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The project's promise in one place: <b>nobody gets anything out of a document they may not read</b>,
 * through any endpoint that can return document content.
 *
 * <pre>
 *                        list        get {id}    delete {id}   search       chat
 *  no token              401         401         401           401          401
 *  no subject            nothing     404         404           nothing      no model call
 *  Mallory (no groups)   nothing     404         404           nothing      no model call
 *  Bob, HR document      absent      404         404           absent       absent from prompt and citations
 *  Bob, shared document  present     200         404 (owner)   present      cited
 *  Alice, owner          present     200         204           present      cited
 * </pre>
 *
 * All three documents are about salaries, so a missing filter would surface them to everyone;
 * {@link #controlUnfilteredSearchWouldReturnAlicesHrChunkToBobsQuery} proves that.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AccessMatrixTest {

    private static final String DOCUMENTS = "/api/v1/documents";
    private static final String SEARCH = "/api/v1/search";
    private static final String CHAT = "/api/v1/chat";
    private static final String QUESTION = "What do engineers earn and when is salary paid?";

    private static final String HR_TITLE = "alice-hr-salary-bands.txt";
    private static final String HR_TEXT = "Salary bands for 2026: engineers earn between two agreed limits.";
    private static final String SHARED_TITLE = "alice-salary-payment-dates.txt";
    private static final String SHARED_TEXT = "Salaries are paid on the last working day of each month.";
    private static final String BOB_TITLE = "bob-engineering-salary-review.txt";
    private static final String BOB_TEXT = "Salary review for the engineering team happens every spring.";

    @Autowired
    MockMvc mvc;

    @Autowired
    DocumentRepository repository;

    @Autowired
    VectorStore vectorStore;

    @Autowired
    StubChatModel chatModel;

    private final List<UUID> createdDocuments = new ArrayList<>();
    private UUID hrDocument;
    private UUID sharedDocument;
    private UUID bobDocument;

    @BeforeEach
    void usersUploadTheirDocuments() throws Exception {
        chatModel.reset();
        hrDocument = upload(alice(), HR_TITLE, HR_TEXT, "hr");
        sharedDocument = upload(alice(), SHARED_TITLE, SHARED_TEXT, "all-staff");
        bobDocument = upload(bob(), BOB_TITLE, BOB_TEXT, "engineering");
    }

    @AfterEach
    void removeCreatedDocuments() {
        IngestionTestSupport.remove(repository, vectorStore, createdDocuments);
    }

    @Test
    void controlUnfilteredSearchWouldReturnAlicesHrChunkToBobsQuery() {
        List<Document> unfiltered = vectorStore.similaritySearch(
                SearchRequest.builder().query(QUESTION).topK(20).similarityThresholdAll().build());

        assertThat(unfiltered).extracting(Document::getText).contains(HR_TEXT, SHARED_TEXT, BOB_TEXT);
    }

    // --- no token -------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyDocumentEndpoint")
    void everyEndpointRejectsRequestsWithoutToken(String name, RequestBuilder request) throws Exception {
        mvc.perform(request).andExpect(status().isUnauthorized());
    }

    static Stream<Arguments> everyDocumentEndpoint() {
        UUID anyId = UUID.randomUUID();
        return Stream.of(
                Arguments.of("list", get(DOCUMENTS)),
                Arguments.of("get", get(DOCUMENTS + "/" + anyId)),
                Arguments.of("delete", delete(DOCUMENTS + "/" + anyId)),
                Arguments.of("upload", multipart(DOCUMENTS).file(textFile("x.txt", "text"))),
                Arguments.of("search", post(SEARCH).contentType(MediaType.APPLICATION_JSON).content(json("query", QUESTION))),
                Arguments.of("chat", post(CHAT).contentType(MediaType.APPLICATION_JSON).content(json("question", QUESTION))));
    }

    // --- valid token, but no subject ------------------------------------------------------------

    @Test
    void tokenWithoutSubjectGetsNothingAnywhere() throws Exception {
        assertSeesNothingOfOthers(noSubject());
    }

    // --- valid token, other user with no groups -------------------------------------------------

    @Test
    void userWithoutGroupsGetsNothingOfOthersAnywhere() throws Exception {
        assertSeesNothingOfOthers(mallory());
    }

    // --- Bob and Alice's HR-only document -------------------------------------------------------

    @Test
    void bobDoesNotSeeAlicesHrDocumentInList() throws Exception {
        String body = list(bob());

        assertThat(ids(body, "$[*].id")).doesNotContain(hrDocument.toString());
        assertThat(body).doesNotContain(HR_TITLE);
    }

    @Test
    void bobGettingOrDeletingAlicesHrDocumentIs404() throws Exception {
        assertThat(bodyOf(get(DOCUMENTS + "/" + hrDocument), bob(), 404)).doesNotContain(HR_TITLE);
        mvc.perform(delete(DOCUMENTS + "/" + hrDocument).with(bob())).andExpect(status().isNotFound());
        assertThat(repository.findById(hrDocument)).isPresent();
    }

    @Test
    void bobsSearchNeverReturnsAlicesHrDocument() throws Exception {
        String body = search(bob());

        assertThat(ids(body, "$[*].documentId")).doesNotContain(hrDocument.toString());
        assertThat(body).doesNotContain(HR_TEXT).doesNotContain(HR_TITLE);
    }

    @Test
    void bobsChatNeverSendsAlicesHrTextToTheModelNorCitesIt() throws Exception {
        String body = chat(bob());

        assertThat(ids(body, "$.citations[*].documentId")).doesNotContain(hrDocument.toString());
        assertThat(body).doesNotContain(HR_TITLE);
        assertThat(chatModel.allPromptText()).doesNotContain(HR_TEXT).doesNotContain(HR_TITLE);
    }

    // --- Bob and the document Alice shared with all staff ---------------------------------------

    @Test
    void bobSeesSearchesAndIsCitedTheSharedDocument() throws Exception {
        assertThat(ids(list(bob()), "$[*].id")).contains(sharedDocument.toString(), bobDocument.toString());
        bodyOf(get(DOCUMENTS + "/" + sharedDocument), bob(), 200);
        assertThat(ids(search(bob()), "$[*].documentId")).contains(sharedDocument.toString());
        assertThat(ids(chat(bob()), "$.citations[*].documentId")).contains(sharedDocument.toString());
    }

    @Test
    void bobCannotDeleteTheSharedDocumentBecauseHeIsNotTheOwner() throws Exception {
        mvc.perform(delete(DOCUMENTS + "/" + sharedDocument).with(bob())).andExpect(status().isNotFound());

        assertThat(repository.findById(sharedDocument)).isPresent();
    }

    // --- Alice, the owner -----------------------------------------------------------------------

    @Test
    void aliceSeesSearchesAndIsCitedHerHrDocumentButNotBobs() throws Exception {
        assertThat(ids(list(alice()), "$[*].id"))
                .contains(hrDocument.toString(), sharedDocument.toString())
                .doesNotContain(bobDocument.toString());
        bodyOf(get(DOCUMENTS + "/" + hrDocument), alice(), 200);
        assertThat(ids(search(alice()), "$[*].documentId"))
                .contains(hrDocument.toString())
                .doesNotContain(bobDocument.toString());
        assertThat(ids(chat(alice()), "$.citations[*].documentId")).contains(hrDocument.toString());
        assertThat(chatModel.allPromptText()).doesNotContain(BOB_TEXT);
    }

    @Test
    void aliceDeletesHerDocumentAndItDisappearsFromSearch() throws Exception {
        mvc.perform(delete(DOCUMENTS + "/" + hrDocument).with(alice())).andExpect(status().isNoContent());

        assertThat(ids(search(alice()), "$[*].documentId")).doesNotContain(hrDocument.toString());
    }

    // --- helpers --------------------------------------------------------------------------------

    /** Neither Alice's nor Bob's documents are reachable, and the model is never called. */
    private void assertSeesNothingOfOthers(JwtRequestPostProcessor caller) throws Exception {
        List<String> othersDocuments = List.of(hrDocument.toString(), sharedDocument.toString(), bobDocument.toString());

        assertThat(ids(list(caller), "$[*].id")).doesNotContainAnyElementsOf(othersDocuments);
        for (UUID document : List.of(hrDocument, sharedDocument, bobDocument)) {
            bodyOf(get(DOCUMENTS + "/" + document), caller, 404);
            mvc.perform(delete(DOCUMENTS + "/" + document).with(caller)).andExpect(status().isNotFound());
        }
        assertThat(ids(search(caller), "$[*].documentId")).doesNotContainAnyElementsOf(othersDocuments);
        assertThat(ids(chat(caller), "$.citations[*].documentId")).isEmpty();
        assertThat(chatModel.prompts()).isEmpty();
    }

    private UUID upload(JwtRequestPostProcessor owner, String title, String text, String group) throws Exception {
        String body = mvc.perform(multipart(DOCUMENTS).file(textFile(title, text)).param("groups", group).with(owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        createdDocuments.add(id);
        return id;
    }

    private String list(JwtRequestPostProcessor caller) throws Exception {
        return bodyOf(get(DOCUMENTS), caller, 200);
    }

    private String search(JwtRequestPostProcessor caller) throws Exception {
        return bodyOf(post(SEARCH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + QUESTION + "\",\"topK\":20}"), caller, 200);
    }

    private String chat(JwtRequestPostProcessor caller) throws Exception {
        return bodyOf(post(CHAT).contentType(MediaType.APPLICATION_JSON).content(json("question", QUESTION)), caller, 200);
    }

    private String bodyOf(MockHttpServletRequestBuilder request, JwtRequestPostProcessor caller, int expectedStatus) throws Exception {
        return mvc.perform(request.with(caller))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private static List<String> ids(String body, String path) {
        return JsonPath.read(body, path);
    }

    private static String json(String field, String value) {
        return "{\"" + field + "\":\"" + value + "\"}";
    }

    private static JwtRequestPostProcessor alice() {
        return jwt().jwt(token -> token.subject("sub-alice").claim("groups", List.of("all-staff", "hr")));
    }

    private static JwtRequestPostProcessor bob() {
        return jwt().jwt(token -> token.subject("sub-bob").claim("groups", List.of("all-staff", "engineering")));
    }

    private static JwtRequestPostProcessor mallory() {
        return jwt().jwt(token -> token.subject("sub-mallory"));
    }

    private static JwtRequestPostProcessor noSubject() {
        return jwt().jwt(token -> token
                .claims(claims -> claims.remove("sub"))
                .claim("groups", List.of("all-staff", "hr", "engineering")));
    }

}
