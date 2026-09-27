package dev.whitemes.tickettriage.domain;

/** Three-state lifecycle of a ticket. */
public enum TicketStatus {
    /** Automatically routed — classifier confidence met the threshold. */
    ROUTED,
    /** Waiting for human validation — confidence below threshold or invalid result. */
    PENDING_HUMAN,
    /** Confirmed or corrected by a human agent. */
    VALIDATED
}
