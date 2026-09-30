package dev.whitemes.tickettriage.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.whitemes.tickettriage.classifier.ClassificationResultParser;
import dev.whitemes.tickettriage.classifier.GraniteClassifier;
import dev.whitemes.tickettriage.classifier.PersonalDataMasker;
import dev.whitemes.tickettriage.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TicketService. {@code TicketClassifier} is sealed and cannot be mocked: the service
 * runs with the real {@link GraniteClassifier}, whose LangChain4j chat model is stubbed.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private ChatLanguageModel chatModel;

    @Mock
    private TicketRepository repository;

    private final TeamRouter teamRouter = new TeamRouter();
    private final PersonalDataMasker masker = new PersonalDataMasker();

    private TicketService service;

    @BeforeEach
    void setUp() {
        var classifier = new GraniteClassifier(masker, new ClassificationResultParser(), chatModel);
        // threshold = 0.7
        service = new TicketService(classifier, masker, repository, teamRouter, 0.7);
    }

    @Test
    void highConfidenceTicketIsRouted() {
        givenModelAnswers("NETWORK", "HIGH", "0.9");
        givenRepositorySavesTickets();

        Ticket ticket = service.submit("Mon vpn ne fonctionne pas");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ROUTED);
        assertThat(ticket.getCategory()).isEqualTo(Category.NETWORK);
    }

    @Test
    void lowConfidenceTicketIsPendingHuman() {
        givenModelAnswers("OTHER", "LOW", "0.4");
        givenRepositorySavesTickets();

        Ticket ticket = service.submit("Texte vague");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING_HUMAN);
    }

    @Test
    void exactThresholdIsRouted() {
        givenModelAnswers("SOFTWARE", "MEDIUM", "0.7");
        givenRepositorySavesTickets();

        Ticket ticket = service.submit("Crash application");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ROUTED);
    }

    @Test
    void criticalTicketIsAlwaysPendingHumanEvenWithHighConfidence() {
        givenModelAnswers("SECURITY", "CRITICAL", "0.99");
        givenRepositorySavesTickets();

        Ticket ticket = service.submit("Ransomware détecté sur le poste de travail.");

        assertThat(ticket.getStatus())
                .as("CRITICAL tickets must always go to human review, regardless of confidence")
                .isEqualTo(TicketStatus.PENDING_HUMAN);
    }

    @Test
    void classifierFailureSendsTicketToHumanQueue() {
        when(chatModel.generate(anyList())).thenThrow(
                new RuntimeException(new java.net.ConnectException("Connection refused")));
        givenRepositorySavesTickets();

        Ticket ticket = service.submit("Impossible d'ouvrir le logiciel de crédit");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING_HUMAN);
        assertThat(ticket.getCategory()).isEqualTo(Category.OTHER);
        assertThat(ticket.getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(ticket.getConfidence()).isZero();
        assertThat(ticket.getRawText()).isEqualTo("Impossible d'ouvrir le logiciel de crédit");
        verify(repository).save(any(Ticket.class));
    }

    @Test
    void outOfRangeConfidenceSendsTicketToHumanQueue() {
        // The model answers 95: ClassificationResult refuses it, the classifier fails.
        givenModelAnswers("NETWORK", "LOW", "95");
        givenRepositorySavesTickets();

        Ticket ticket = service.submit("Le Wi-Fi coupe toutes les 5 minutes");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING_HUMAN);
        verify(repository).save(any(Ticket.class));
    }

    @Test
    void failedClassificationGoesToHumanQueueEvenWithZeroThreshold() {
        var classifier = new GraniteClassifier(masker, new ClassificationResultParser(), chatModel);
        var permissiveService = new TicketService(classifier, masker, repository, teamRouter, 0.0);
        when(chatModel.generate(anyList())).thenThrow(
                new RuntimeException(new java.net.ConnectException("Connection refused")));
        givenRepositorySavesTickets();

        Ticket ticket = permissiveService.submit("Impossible d'ouvrir le logiciel de crédit");

        assertThat(ticket.getStatus())
                .as("the fallback result never passes the threshold test, whatever the threshold")
                .isEqualTo(TicketStatus.PENDING_HUMAN);
    }

    @Test
    void validateUpdatesTicketToValidated() {
        var existing = new Ticket();
        existing.setId(1L);
        existing.setCategory(Category.OTHER);
        existing.setPriority(Priority.LOW);
        existing.setStatus(TicketStatus.PENDING_HUMAN);
        existing.setRawText("Texte vague");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        givenRepositorySavesTickets();

        Ticket validated = service.validate(1L, Category.NETWORK, Priority.HIGH);

        assertThat(validated.getStatus()).isEqualTo(TicketStatus.VALIDATED);
        assertThat(validated.getCategory()).isEqualTo(Category.NETWORK);
        assertThat(validated.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(validated.getTeam()).isNotBlank();
    }

    // --- Helpers ---

    private void givenModelAnswers(String category, String priority, String confidence) {
        var json = """
                {"category":"%s","priority":"%s","summary":"Résumé.","justification":"Justification.","confidence":%s}
                """.formatted(category, priority, confidence);
        when(chatModel.generate(anyList())).thenReturn(Response.from(AiMessage.from(json)));
    }

    private void givenRepositorySavesTickets() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }
}
