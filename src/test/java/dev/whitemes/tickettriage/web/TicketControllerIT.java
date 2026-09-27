package dev.whitemes.tickettriage.web;

import dev.whitemes.tickettriage.domain.TicketRepository;
import dev.whitemes.tickettriage.domain.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests: real Spring Boot context, H2 in-memory, FakeClassifier.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TicketControllerIT {

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
