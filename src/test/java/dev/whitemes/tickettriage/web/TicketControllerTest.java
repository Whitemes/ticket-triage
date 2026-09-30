package dev.whitemes.tickettriage.web;

import dev.whitemes.tickettriage.domain.Ticket;
import dev.whitemes.tickettriage.domain.TicketRepository;
import dev.whitemes.tickettriage.domain.TicketStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests: real Spring Boot context, H2 in-memory, FakeClassifier.
 * Named *Test so that Surefire runs it in mvn verify.
 */
@SpringBootTest(properties = "classifier.type=fake")
@AutoConfigureMockMvc
@Transactional
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository repository;

    @Test
    void postTicketCreatesTicketInDatabase() throws Exception {
        long before = repository.count();

        mockMvc.perform(post("/tickets")
                        .param("rawText", "Mon vpn ne fonctionne plus"))
                .andExpect(status().is3xxRedirection());

        assertThat(repository.count()).isEqualTo(before + 1);
    }

    @Test
    void postVagueTextCreatesTicketWithPendingHumanStatus() throws Exception {
        mockMvc.perform(post("/tickets")
                        .param("rawText", "ok"))
                .andExpect(status().is3xxRedirection());

        boolean hasPending = repository.findByStatus(TicketStatus.PENDING_HUMAN)
                .stream().anyMatch(t -> "ok".equals(t.getRawText()));
        assertThat(hasPending).isTrue();
    }

    @Test
    void ticketPageShowsTextSentToModel() throws Exception {
        String location = mockMvc.perform(post("/tickets")
                        .param("rawText", "Mot de passe expiré, contact jean.test@example.com"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(view().name("ticket-result"))
                .andExpect(content().string(containsString("[EMAIL]")));
    }

    @Test
    void validationRedirectsToValidatedTicket() throws Exception {
        mockMvc.perform(post("/tickets").param("rawText", "ok"))
                .andExpect(status().is3xxRedirection());
        Ticket pending = repository.findByStatus(TicketStatus.PENDING_HUMAN).stream()
                .filter(t -> "ok".equals(t.getRawText()))
                .findFirst().orElseThrow();

        mockMvc.perform(post("/human-queue/" + pending.getId() + "/validate")
                        .param("category", "NETWORK")
                        .param("priority", "HIGH"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/" + pending.getId()));

        assertThat(repository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.VALIDATED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   \n  ", "  "})
    void blankTicketIsRefusedOnTheForm(String rawText) throws Exception {
        long before = repository.count();

        mockMvc.perform(post("/tickets").param("rawText", rawText))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("error", containsString("Le ticket est vide")))
                .andExpect(content().string(containsString("Le ticket est vide")));

        assertThat(repository.count()).as("no ticket created, classifier not called").isEqualTo(before);
    }

    @Test
    void tooLongTicketIsRefusedOnTheForm() throws Exception {
        long before = repository.count();

        mockMvc.perform(post("/tickets").param("rawText", "a".repeat(5001)))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("error", containsString("longueur maximale de 5000")))
                .andExpect(content().string(containsString("longueur maximale de 5000")));

        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void ticketAtMaxLengthIsAccepted() throws Exception {
        mockMvc.perform(post("/tickets").param("rawText", "a".repeat(5000)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void crlfLineBreaksCountAsOneCharacterLikeTheBrowser() throws Exception {
        // 7 500 characters as sent (CRLF), 5 000 as counted by the browser's maxlength: accepted.
        mockMvc.perform(post("/tickets").param("rawText", "a\r\n".repeat(2500)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void formDeclaresMaxLength() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("maxlength=\"5000\"")));
    }

    @Test
    void unknownTicketReturns404() throws Exception {
        mockMvc.perform(get("/tickets/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatingUnknownTicketReturns404() throws Exception {
        mockMvc.perform(post("/human-queue/9999/validate")
                        .param("category", "NETWORK")
                        .param("priority", "HIGH"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getHumanQueueReturns200() throws Exception {
        mockMvc.perform(get("/human-queue"))
                .andExpect(status().isOk())
                .andExpect(view().name("human-queue"));
    }

    @Test
    void getIndexReturns200() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }
}
