package dev.whitemes.tickettriage.service;

import dev.whitemes.tickettriage.classifier.ClassificationResult;
import dev.whitemes.tickettriage.classifier.PersonalDataMasker;
import dev.whitemes.tickettriage.classifier.TicketClassifier;
import dev.whitemes.tickettriage.domain.*;
import dev.whitemes.tickettriage.exception.TicketNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the ticket triage flow:
 * submit → mask → classify → persist → route or queue for human review.
 */
@Service
@Transactional
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    /** Used when the classifier fails or returns an invalid result: always sent to the human queue. */
    private static final ClassificationResult FALLBACK = new ClassificationResult(
            Category.OTHER, Priority.MEDIUM,
            "Classification automatique indisponible.",
            "Modèle injoignable ou réponse invalide : qualification par un agent requise.",
            0.0);

    private final TicketClassifier classifier;
    private final PersonalDataMasker masker;
    private final TicketRepository repository;
    private final TeamRouter teamRouter;
    private final double confidenceThreshold;

    public TicketService(TicketClassifier classifier,
                         PersonalDataMasker masker,
                         TicketRepository repository,
                         TeamRouter teamRouter,
                         @Value("${classifier.confidence-threshold:0.7}") double confidenceThreshold) {
        this.classifier = classifier;
        this.masker = masker;
        this.repository = repository;
        this.teamRouter = teamRouter;
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * Submits a raw ticket text, classifies it, and persists it.
     * Status is {@link TicketStatus#ROUTED} when confidence ≥ threshold,
     * {@link TicketStatus#PENDING_HUMAN} otherwise.
     * CRITICAL tickets always go to {@link TicketStatus#PENDING_HUMAN} for human oversight,
     * and so do tickets whose classification failed or was invalid.
     */
    public Ticket submit(String rawText) {
        String masked = masker.mask(rawText);
        log.info("[TRIAGE] Classifieur {} | texte masqué envoyé : {}",
                ClassUtils.getUserClass(classifier).getSimpleName(), masked);

        long start = System.nanoTime();
        ClassificationResult result = classifySafely(masked);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.info("[TRIAGE] Résultat en {} ms : catégorie={}, priorité={}, confiance={}",
                elapsedMs, result.category(), result.priority(), result.confidence());

        Ticket ticket = new Ticket();
        ticket.setRawText(rawText);
        ticket.setMaskedText(masked);
        ticket.setCategory(result.category());
        ticket.setPriority(result.priority());
        ticket.setSummary(result.summary());
        ticket.setJustification(result.justification());
        ticket.setConfidence(result.confidence());
        ticket.setTeam(teamRouter.route(result.category()));

        Optional<String> humanReviewReason = humanReviewReason(result);
        if (humanReviewReason.isPresent()) {
            ticket.setStatus(TicketStatus.PENDING_HUMAN);
            log.info("[TRIAGE] Décision : file humaine ({})", humanReviewReason.get());
        } else {
            ticket.setStatus(TicketStatus.ROUTED);
            log.info("[TRIAGE] Décision : routé vers {} (confiance {} ≥ seuil {})",
                    ticket.getTeam(), result.confidence(), confidenceThreshold);
        }

        return repository.save(ticket);
    }

    /** Returns why the ticket must be reviewed by a human, or empty when it can be routed. */
    private Optional<String> humanReviewReason(ClassificationResult result) {
        if (result == FALLBACK) {
            return Optional.of("classification indisponible");
        }
        if (result.priority() == Priority.CRITICAL) {
            return Optional.of("priorité CRITICAL");
        }
        if (result.confidence() < confidenceThreshold) {
            return Optional.of("confiance " + result.confidence() + " sous le seuil " + confidenceThreshold);
        }
        return Optional.empty();
    }

    /**
     * Calls the classifier and returns {@link #FALLBACK} instead of failing when the model is
     * unreachable, times out, or returns an invalid result. An invalid result cannot be built
     * (the {@link ClassificationResult} constructor rejects it), so it surfaces as an exception.
     * Only the error type is logged, never the ticket text.
     */
    private ClassificationResult classifySafely(String masked) {
        try {
            ClassificationResult result = classifier.classify(masked);
            if (result != null) {
                return result;
            }
            log.warn("[TRIAGE] Résultat de classification absent : passage en file humaine");
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("[TRIAGE] Classification indisponible ({}) : passage en file humaine",
                    cause.getClass().getSimpleName());
        }
        return FALLBACK;
    }

    /** Returns all tickets awaiting human review. */
    @Transactional(readOnly = true)
    public List<Ticket> getPendingTickets() {
        return repository.findByStatus(TicketStatus.PENDING_HUMAN);
    }

    /** Returns all tickets. */
    @Transactional(readOnly = true)
    public List<Ticket> getAllTickets() {
        return repository.findAll();
    }

    /** Returns a single ticket by id, or throws {@link TicketNotFoundException} (HTTP 404) if not found. */
    @Transactional(readOnly = true)
    public Ticket getTicket(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
    }

    /**
     * Validates a ticket from the human queue.
     * The agent may correct the category and/or priority.
     * Status becomes {@link TicketStatus#VALIDATED}.
     */
    public Ticket validate(Long id, Category correctedCategory, Priority correctedPriority) {
        Ticket ticket = getTicket(id);
        ticket.setCategory(correctedCategory);
        ticket.setPriority(correctedPriority);
        ticket.setTeam(teamRouter.route(correctedCategory));
        ticket.setStatus(TicketStatus.VALIDATED);
        return repository.save(ticket);
    }
}
