package dev.whitemes.tickettriage.classifier;

import dev.whitemes.tickettriage.domain.Category;
import dev.whitemes.tickettriage.domain.Priority;

import java.util.Objects;

/**
 * Result produced by a {@link TicketClassifier}. The compact constructor enforces the contract:
 * an instance can only exist with all fields set and a confidence within [0.0, 1.0].
 *
 * @param category      Detected category (never null).
 * @param priority      Detected priority (never null).
 * @param summary       Short summary of the ticket (1–2 sentences, never null).
 * @param justification Why the classifier chose this category (never null).
 * @param confidence    Confidence score in the range [0.0, 1.0].
 */
public record ClassificationResult(
        Category category,
        Priority priority,
        String summary,
        String justification,
        double confidence
) {
    public ClassificationResult {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        Objects.requireNonNull(justification, "justification must not be null");
        if (!(confidence >= 0.0 && confidence <= 1.0)) { // written this way to reject NaN too
            throw new IllegalArgumentException("confidence must be within [0, 1], got " + confidence);
        }
    }
}
