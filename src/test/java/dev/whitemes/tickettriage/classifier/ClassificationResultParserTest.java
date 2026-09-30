package dev.whitemes.tickettriage.classifier;

import dev.whitemes.tickettriage.domain.Category;
import dev.whitemes.tickettriage.domain.Priority;
import dev.whitemes.tickettriage.exception.ClassificationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for {@link ClassificationResultParser}: JSON reply of the model → typed result. */
class ClassificationResultParserTest {

    private final ClassificationResultParser parser = new ClassificationResultParser();

    @Test
    void valid_json_is_converted_to_typed_result() {
        var json = """
                {"category":"SOFTWARE","priority":"MEDIUM",
                 "summary":"Application crash au démarrage.",
                 "justification":"L'application ne répond plus après le lancement.",
                 "confidence":0.88}
                """;

        ClassificationResult result = parser.parse(json);

        assertThat(result.category()).isEqualTo(Category.SOFTWARE);
        assertThat(result.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(result.summary()).isEqualTo("Application crash au démarrage.");
        assertThat(result.justification()).isEqualTo("L'application ne répond plus après le lancement.");
        assertThat(result.confidence()).isEqualTo(0.88);
    }

    @Test
    void lower_case_enum_values_are_accepted() {
        var json = """
                {"category":"network","priority":"high","summary":"VPN coupé.",
                 "justification":"Problème réseau.","confidence":0.9}
                """;

        ClassificationResult result = parser.parse(json);

        assertThat(result.category()).isEqualTo(Category.NETWORK);
        assertThat(result.priority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void malformed_json_is_rejected() {
        assertThatThrownBy(() -> parser.parse("this is not json at all"))
                .isInstanceOf(ClassificationException.class)
                .hasMessageContaining("Invalid model response");
    }

    @Test
    void unknown_category_is_rejected() {
        var json = """
                {"category":"UNKNOWN_CATEGORY","priority":"HIGH","summary":"Problème inconnu.",
                 "justification":"Catégorie fictive.","confidence":0.70}
                """;

        assertThatThrownBy(() -> parser.parse(json)).isInstanceOf(ClassificationException.class);
    }

    @Test
    void unknown_priority_is_rejected() {
        var json = """
                {"category":"NETWORK","priority":"SUPER_CRITICAL","summary":"Réseau coupé.",
                 "justification":"Coupure réseau.","confidence":0.80}
                """;

        assertThatThrownBy(() -> parser.parse(json)).isInstanceOf(ClassificationException.class);
    }

    @Test
    void out_of_range_confidence_is_rejected() {
        var json = """
                {"category":"NETWORK","priority":"LOW","summary":"Réseau lent.",
                 "justification":"Lenteur réseau.","confidence":95}
                """;

        assertThatThrownBy(() -> parser.parse(json)).isInstanceOf(ClassificationException.class);
    }

    @Test
    void missing_summary_is_rejected() {
        var json = """
                {"category":"NETWORK","priority":"LOW",
                 "justification":"Lenteur réseau.","confidence":0.8}
                """;

        assertThatThrownBy(() -> parser.parse(json)).isInstanceOf(ClassificationException.class);
    }

    @Test
    void unknown_field_is_rejected() {
        var json = """
                {"category":"NETWORK","priority":"LOW","summary":"Réseau lent.",
                 "justification":"Lenteur réseau.","confidence":0.8,"team":"Équipe Réseau"}
                """;

        assertThatThrownBy(() -> parser.parse(json)).isInstanceOf(ClassificationException.class);
    }
}
