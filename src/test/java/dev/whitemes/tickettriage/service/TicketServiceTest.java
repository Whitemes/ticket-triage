package dev.whitemes.tickettriage.service;

import dev.whitemes.tickettriage.classifier.ClassificationResult;
import dev.whitemes.tickettriage.classifier.TicketClassifier;
import dev.whitemes.tickettriage.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for TicketService — TicketClassifier is mocked. */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketClassifier classifier;

    @Mock
    private TicketRepository repository;

    private final TeamRouter teamRouter = new TeamRouter();

    private TicketService service;

    @BeforeEach
    void setUp() {
        // threshold = 0.7
        service = new TicketService(classifier, repository, teamRouter, 0.7);
    }

    @Test
    void highConfidenceTicketIsRouted() {
        when(classifier.classify(any())).thenReturn(
                new ClassificationResult(Category.NETWORK, Priority.HIGH,
                        "Summary", "Justification", 0.9));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Ticket ticket = service.submit("Mon vpn ne fonctionne pas");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ROUTED);
        assertThat(ticket.getCategory()).isEqualTo(Category.NETWORK);
    }

    @Test
    void lowConfidenceTicketIsPendingHuman() {
        when(classifier.classify(any())).thenReturn(
                new ClassificationResult(Category.OTHER, Priority.LOW,
                        "Summary", "Justification", 0.4));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Ticket ticket = service.submit("Texte vague");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING_HUMAN);
    }

    @Test
    void exactThresholdIsRouted() {
        when(classifier.classify(any())).thenReturn(
                new ClassificationResult(Category.SOFTWARE, Priority.MEDIUM,
                        "Summary", "Justification", 0.7));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Ticket ticket = service.submit("Crash application");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ROUTED);
    }

    @Test
    void validateUpdatesTicketToValidated() {
        Ticket existing = new Ticket();
        existing.setId(1L);
        existing.setCategory(Category.OTHER);
        existing.setPriority(Priority.LOW);
        existing.setStatus(TicketStatus.PENDING_HUMAN);
        existing.setRawText("Texte vague");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Ticket validated = service.validate(1L, Category.NETWORK, Priority.HIGH);

        assertThat(validated.getStatus()).isEqualTo(TicketStatus.VALIDATED);
        assertThat(validated.getCategory()).isEqualTo(Category.NETWORK);
        assertThat(validated.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(validated.getTeam()).isNotBlank();
    }
}
