package dev.whitemes.tickettriage.classifier;

import dev.whitemes.tickettriage.domain.Category;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test against a real Ollama instance.
 *
 * <p>This test is tagged {@code ollama-live} and is <strong>excluded from {@code mvn verify}</strong>
 * by the Surefire configuration in {@code pom.xml}. It is meant to be run manually before a demo:
 *
 * <pre>
 *   $env:OLLAMA_IT='true'; mvn test -Pollama-live      (Windows PowerShell)
 * </pre>
 *
 * <p>The test skips automatically (via {@link Assumptions#assumeTrue}) when the
 * environment variable {@code OLLAMA_IT} is absent, so it never fails accidentally in CI.
 */
@Tag("ollama-live")
class GraniteClassifierOllamaIT {

    @Test
    void ticket_vpn_classifie_en_network_avec_vrai_ollama() {
        // Skip unless explicitly enabled
        Assumptions.assumeTrue(
                System.getenv("OLLAMA_IT") != null,
                "Skipped: set OLLAMA_IT=true to run against a live Ollama instance.");

        // Build the real classifier pointing at localhost Ollama
        String baseUrl = System.getenv("OLLAMA_BASE_URL") != null
                ? System.getenv("OLLAMA_BASE_URL")
                : "http://localhost:11434";

        // Uses the public production constructor (builds a real OllamaChatModel internally)
        GraniteClassifier realClassifier = new GraniteClassifier(
                new PersonalDataMasker(), baseUrl, "granite4:micro", 120);

        ClassificationResult result = realClassifier.classify(
                "Impossible de me connecter au VPN depuis ce matin, j'ai l'erreur 619.");

        assertThat(result.category())
                .as("Granite should classify a VPN connectivity issue as NETWORK")
                .isEqualTo(Category.NETWORK);
    }
}
