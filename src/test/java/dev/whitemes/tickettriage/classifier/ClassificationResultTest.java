package dev.whitemes.tickettriage.classifier;

import dev.whitemes.tickettriage.domain.Category;
import dev.whitemes.tickettriage.domain.Priority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The compact constructor of {@link ClassificationResult} enforces its contract. */
class ClassificationResultTest {

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.7, 1.0})
    void valid_result_is_accepted(double confidence) {
        ClassificationResult result = new ClassificationResult(
                Category.NETWORK, Priority.HIGH, "Résumé", "Justification", confidence);

        assertThat(result.confidence()).isEqualTo(confidence);
    }

    @Test
    void null_category_is_rejected() {
        assertThatThrownBy(() -> new ClassificationResult(null, Priority.HIGH, "Résumé", "Justification", 0.9))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("category");
    }

    @Test
    void null_priority_is_rejected() {
        assertThatThrownBy(() -> new ClassificationResult(Category.NETWORK, null, "Résumé", "Justification", 0.9))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("priority");
    }

    @Test
    void null_summary_is_rejected() {
        assertThatThrownBy(() -> new ClassificationResult(Category.NETWORK, Priority.HIGH, null, "Justification", 0.9))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("summary");
    }

    @Test
    void null_justification_is_rejected() {
        assertThatThrownBy(() -> new ClassificationResult(Category.NETWORK, Priority.HIGH, "Résumé", null, 0.9))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("justification");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, 95.0, Double.NaN})
    void confidence_outside_unit_interval_is_rejected(double confidence) {
        assertThatThrownBy(() -> new ClassificationResult(
                Category.NETWORK, Priority.HIGH, "Résumé", "Justification", confidence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }
}
