package dev.whitemes.tickettriage.classifier;

/** Contract for all ticket classifiers (fake or AI-backed). */
public interface TicketClassifier {

    /**
     * Classifies a raw ticket text.
     *
     * @param text the raw (already anonymised) ticket text
     * @return a {@link ClassificationResult} — never null
     */
    ClassificationResult classify(String text);
}
