package dev.whitemes.tickettriage.web;

import dev.whitemes.tickettriage.domain.Category;
import dev.whitemes.tickettriage.domain.Priority;
import dev.whitemes.tickettriage.domain.Ticket;
import dev.whitemes.tickettriage.service.TicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/** Handles all HTTP routes for the ticket triage UI. */
@Controller
public class TicketController {

    private final TicketService ticketService;
    private final int maxLength;

    public TicketController(TicketService ticketService,
                            @Value("${ticket.max-length:5000}") int maxLength) {
        this.ticketService = ticketService;
        this.maxLength = maxLength;
    }

    /** Exposes the maximum ticket length to the views (maxlength attribute of the form). */
    @ModelAttribute("maxLength")
    public int maxLength() {
        return maxLength;
    }

    /** Home page — ticket submission form. */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * Receives the form submission and redirects to the result page. A blank or too long ticket
     * is sent back to the form with a message, without calling the classifier.
     */
    @PostMapping("/tickets")
    public String submit(@RequestParam String rawText, Model model) {
        Optional<String> error = validationError(rawText);
        if (error.isPresent()) {
            model.addAttribute("error", error.get());
            model.addAttribute("rawText", rawText);
            return "index";
        }
        Ticket ticket = ticketService.submit(rawText);
        return "redirect:/tickets/" + ticket.getId();
    }

    /** Ticket detail page. */
    @GetMapping("/tickets/{id}")
    public String ticketDetail(@PathVariable Long id, Model model) {
        model.addAttribute("ticket", ticketService.getTicket(id));
        return "ticket-result";
    }

    /** Human review queue — lists all PENDING_HUMAN tickets. */
    @GetMapping("/human-queue")
    public String humanQueue(Model model) {
        model.addAttribute("tickets", ticketService.getPendingTickets());
        model.addAttribute("categories", Category.values());
        model.addAttribute("priorities", Priority.values());
        return "human-queue";
    }

    /** Validates (and optionally corrects) a ticket from the human queue, then shows the updated ticket. */
    @PostMapping("/human-queue/{id}/validate")
    public String validate(@PathVariable Long id,
                           @RequestParam Category category,
                           @RequestParam Priority priority) {
        ticketService.validate(id, category, priority);
        return "redirect:/tickets/" + id;
    }

    /** Returns why the submitted text is refused, or empty when it can be classified. */
    private Optional<String> validationError(String rawText) {
        if (rawText.isBlank()) {
            return Optional.of("Le ticket est vide : décrivez le problème rencontré.");
        }
        // Browsers count a line break as one character but send CRLF: measure the normalised text.
        if (rawText.replace("\r\n", "\n").length() > maxLength) {
            return Optional.of("Le ticket dépasse la longueur maximale de " + maxLength + " caractères.");
        }
        return Optional.empty();
    }
}
