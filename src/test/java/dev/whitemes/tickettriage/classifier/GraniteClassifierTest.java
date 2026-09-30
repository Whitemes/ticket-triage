package dev.whitemes.tickettriage.classifier;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.whitemes.tickettriage.domain.Category;
import dev.whitemes.tickettriage.domain.Priority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GraniteClassifier}.
 * The Ollama HTTP layer is mocked via the {@link ChatLanguageModel} interface —
 * no running Ollama instance required.
 */
@ExtendWith(MockitoExtension.class)
class GraniteClassifierTest {

    @Mock
    private ChatLanguageModel chatModel;

    private GraniteClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new GraniteClassifier(new PersonalDataMasker(), chatModel);
    }

    // --- Nominal case ---

    @Test
    void nominal_valid_json_response_returns_classification_result() {
        String json = """
                {"category":"SOFTWARE","priority":"MEDIUM",
                 "summary":"Application crash au démarrage.",
                 "justification":"L'application ne répond plus après le lancement.",
                 "confidence":0.88}
                """;
        givenModelReturns(json);

        ClassificationResult result = classifier.classify("Mon application plante au démarrage.");

        assertThat(result.category()).isEqualTo(Category.SOFTWARE);
        assertThat(result.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(result.confidence()).isEqualTo(0.88);
    }

    // --- Obligatory VPN case ---

    @Test
    void ticket_vpn_classifie_en_network() {
        String json = """
                {"category":"NETWORK","priority":"HIGH",
                 "summary":"Connexion VPN impossible, erreur 619.",
                 "justification":"Erreur VPN — problème réseau ou de configuration tunnel.",
                 "confidence":0.95}
                """;
        givenModelReturns(json);

        ClassificationResult result = classifier.classify(
                "Impossible de me connecter au VPN depuis ce matin, j'ai l'erreur 619.");

        assertThat(result.category()).isEqualTo(Category.NETWORK);
        assertThat(result.priority()).isEqualTo(Priority.HIGH);
        assertThat(result.confidence()).isGreaterThan(0.7);
    }

    // --- Degraded: malformed JSON ---

    @Test
    void malformed_json_response_throws_classification_exception() {
        givenModelReturns("this is not json at all");

        assertThatThrownBy(() -> classifier.classify("Some ticket text"))
                .isInstanceOf(GraniteClassifier.ClassificationException.class)
                .hasMessageContaining("Invalid model response");
    }

    // --- Degraded: unknown enum value ---

    @Test
    void unknown_category_in_response_throws_classification_exception() {
        String json = """
                {"category":"UNKNOWN_CATEGORY","priority":"HIGH",
                 "summary":"Problème inconnu.",
                 "justification":"Catégorie fictive.",
                 "confidence":0.70}
                """;
        givenModelReturns(json);

        assertThatThrownBy(() -> classifier.classify("Some ticket"))
                .isInstanceOf(GraniteClassifier.ClassificationException.class);
    }

    // --- Degraded: unknown priority value ---

    @Test
    void unknown_priority_in_response_throws_classification_exception() {
        String json = """
                {"category":"NETWORK","priority":"SUPER_CRITICAL",
                 "summary":"Réseau coupé.",
                 "justification":"Coupure réseau.",
                 "confidence":0.80}
                """;
        givenModelReturns(json);

        assertThatThrownBy(() -> classifier.classify("Réseau coupé"))
                .isInstanceOf(GraniteClassifier.ClassificationException.class);
    }

    // --- Degraded: response that breaks the ClassificationResult contract ---

    @Test
    void out_of_range_confidence_in_response_throws_classification_exception() {
        String json = """
                {"category":"NETWORK","priority":"LOW",
                 "summary":"Réseau lent.",
                 "justification":"Lenteur réseau.",
                 "confidence":95}
                """;
        givenModelReturns(json);

        assertThatThrownBy(() -> classifier.classify("Réseau lent"))
                .isInstanceOf(GraniteClassifier.ClassificationException.class);
    }

    @Test
    void missing_summary_in_response_throws_classification_exception() {
        String json = """
                {"category":"NETWORK","priority":"LOW",
                 "justification":"Lenteur réseau.",
                 "confidence":0.8}
                """;
        givenModelReturns(json);

        assertThatThrownBy(() -> classifier.classify("Réseau lent"))
                .isInstanceOf(GraniteClassifier.ClassificationException.class);
    }

    // --- Constructor contract (no network call: the Ollama client is only built) ---

    @Test
    void blank_base_url_is_rejected() {
        assertThatThrownBy(() -> new GraniteClassifier(new PersonalDataMasker(), " ", "granite4:micro", 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ollama.base-url");
    }

    @Test
    void blank_model_name_is_rejected() {
        assertThatThrownBy(() -> new GraniteClassifier(new PersonalDataMasker(), "http://localhost:11434", "", 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ollama.model-name");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void non_positive_timeout_is_rejected(int timeoutSeconds) {
        assertThatThrownBy(() -> new GraniteClassifier(
                new PersonalDataMasker(), "http://localhost:11434", "granite4:micro", timeoutSeconds))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ollama.timeout-seconds");
    }

    @Test
    void valid_configuration_is_accepted() {
        assertThatCode(() -> new GraniteClassifier(
                new PersonalDataMasker(), "http://localhost:11434", "granite4:micro", 60))
                .doesNotThrowAnyException();
    }

    // --- Personal data is masked before sending ---

    @Test
    void personal_data_is_masked_before_classification() {
        String json = """
                {"category":"ACCESS","priority":"HIGH",
                 "summary":"Compte verrouillé.",
                 "justification":"Problème d'accès.",
                 "confidence":0.90}
                """;
        // We capture what was actually sent to the model via a lenient any-list match
        when(chatModel.generate(anyList()))
                .thenAnswer(inv -> {
                    List<?> messages = inv.getArgument(0);
                    // The last message (user) must not contain the raw e-mail
                    String userText = messages.get(1).toString();
                    assertThat(userText).doesNotContain("user@banque.fr")
                            .contains("[EMAIL]");
                    return Response.from(AiMessage.from(json));
                });

        classifier.classify("Mon compte user@banque.fr est bloqué.");
    }

    // --- Helper ---

    private void givenModelReturns(String json) {
        when(chatModel.generate(anyList()))
                .thenReturn(Response.from(AiMessage.from(json)));
    }
}
