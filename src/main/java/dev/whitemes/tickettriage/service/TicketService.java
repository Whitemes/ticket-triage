package dev.whitemes.tickettriage.service;

import dev.whitemes.tickettriage.classifier.ClassificationResult;
import dev.whitemes.tickettriage.classifier.PersonalDataMasker;
import dev.whitemes.tickettriage.classifier.TicketClassifier;
import dev.whitemes.tickettriage.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestrates the ticket triage flow:
 * submit → mask → classify → persist → route or queue for human review.
 */
@Service
@Transactional
public class TicketService {

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
     * CRITICAL tickets always go to {@link TicketStatus#PENDING_HUMAN} for human oversight.
     */
    public Ticket submit(String rawText) {
        String masked = masker.mask(rawText);
        ClassificationResult result = classifier.classify(masked);

        Ticket ticket = new Ticket();
        ticket.setRawText(rawText);
        ticket.setMaskedText(masked);
        ticket.setCategory(result.category());
        ticket.setPriority(result.priority());
        ticket.setSummary(result.summary());
        ticket.setJustification(result.justification());
        ticket.setConfidence(result.confidence());
        ticket.setTeam(teamRouter.route(result.category()));

        boolean critical = result.priority() == Priority.CRITICAL;
        if (!critical && result.confidence() >= confidenceThreshold) {
            ticket.setStatus(TicketStatus.ROUTED);
        } else {
            ticket.setStatus(TicketStatus.PENDING_HUMAN);
        }

        return repository.save(ticket);
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

    /** Returns a single ticket by id, or throws if not found. */
    @Transactional(readOnly = true)
    public Ticket getTicket(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket introuvable : " + id));
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
