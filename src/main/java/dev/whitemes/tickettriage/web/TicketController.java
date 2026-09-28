package dev.whitemes.tickettriage.web;

import dev.whitemes.tickettriage.domain.Category;
import dev.whitemes.tickettriage.domain.Priority;
import dev.whitemes.tickettriage.domain.Ticket;
import dev.whitemes.tickettriage.service.TicketService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** Handles all HTTP routes for the ticket triage UI. */
@Controller
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** Home page — ticket submission form. */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /** Receives the form submission and redirects to the result page. */
    @PostMapping("/tickets")
    public String submit(@RequestParam String rawText, Model model) {
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
}
