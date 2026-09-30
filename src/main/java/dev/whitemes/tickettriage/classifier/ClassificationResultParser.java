package dev.whitemes.tickettriage.classifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.whitemes.tickettriage.domain.Category;
import dev.whitemes.tickettriage.domain.Priority;
import dev.whitemes.tickettriage.exception.ClassificationException;
import org.springframework.stereotype.Component;

/**
 * Turns the model's JSON reply into a {@link ClassificationResult}: Jackson reads an intermediate
 * record, then category and priority are converted to their closed enums. Any failure, including
 * a value refused by the {@link ClassificationResult} contract, becomes a {@link ClassificationException}.
 */
@Component
public class ClassificationResultParser {

    // A dedicated mapper rather than Spring's: an unknown field in the model's reply stays an error.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClassificationResult parse(String json) {
        try {
            RawResult raw = objectMapper.readValue(json, RawResult.class);
            Category category = Category.valueOf(raw.category().toUpperCase());
            Priority priority = Priority.valueOf(raw.priority().toUpperCase());
            return new ClassificationResult(category, priority, raw.summary(),
                    raw.justification(), raw.confidence());
        } catch (Exception e) {
            throw new ClassificationException("Invalid model response: " + e.getMessage(), e);
        }
    }

    /** Intermediate record for Jackson deserialization of the model's JSON reply. */
    private record RawResult(
            String category,
            String priority,
            String summary,
            String justification,
            double confidence) {}
}
