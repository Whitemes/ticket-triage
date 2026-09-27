package dev.whitemes.tickettriage.classifier;

import dev.whitemes.tickettriage.domain.Category;
import dev.whitemes.tickettriage.domain.Priority;

/**
 * Result produced by a {@link TicketClassifier}.
 *
 * @param category      Detected category (never null).
 * @param priority      Detected priority (never null).
 * @param summary       Short summary of the ticket (1–2 sentences).
 * @param justification Why the classifier chose this category.
 * @param confidence    Confidence score in the range [0.0, 1.0].
 */
public record ClassificationResult(
        Category category,
        Priority priority,
        String summary,
        String justification,
        double confidence
) {}
