package dev.whitemes.tickettriage.classifier;

/**
 * Contract for all ticket classifiers. Sealed: the only implementations are the AI-backed
 * {@link GraniteClassifier} and the keyword-based {@link FakeClassifier}.
 */
public sealed interface TicketClassifier permits GraniteClassifier, FakeClassifier {

    /**
     * Classifies a raw ticket text.
     *
     * @param text the raw (already anonymised) ticket text
     * @return a {@link ClassificationResult} — never null
     */
    ClassificationResult classify(String text);
}
