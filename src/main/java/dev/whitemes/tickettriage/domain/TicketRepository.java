package dev.whitemes.tickettriage.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** JPA repository for {@link Ticket}. */
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByStatus(TicketStatus status);
}
