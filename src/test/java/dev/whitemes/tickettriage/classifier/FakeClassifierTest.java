package dev.whitemes.tickettriage.classifier;

import dev.whitemes.tickettriage.domain.Category;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies keyword routing and the low-confidence fallback of FakeClassifier. */
class FakeClassifierTest {

    private final FakeClassifier classifier = new FakeClassifier();

    @Test
    void networkKeywordYieldsNetworkCategory() {
        ClassificationResult result = classifier.classify("Mon vpn ne fonctionne plus depuis ce matin");
        assertThat(result.category()).isEqualTo(Category.NETWORK);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.7);
    }

    @Test
    void shortTextYieldsOtherWithLowConfidence() {
        ClassificationResult result = classifier.classify("Problème");
        assertThat(result.category()).isEqualTo(Category.OTHER);
        assertThat(result.confidence()).isLessThan(0.7);
    }

    @Test
    void unknownTextYieldsOtherWithLowConfidence() {
        ClassificationResult result = classifier.classify("Je ne sais pas trop ce qui se passe avec mon poste");
        assertThat(result.category()).isEqualTo(Category.OTHER);
        assertThat(result.confidence()).isLessThan(0.7);
    }

    @Test
    void passwordKeywordYieldsAccessCategory() {
        ClassificationResult result = classifier.classify("Je n'arrive plus à changer mon mot de passe");
        assertThat(result.category()).isEqualTo(Category.ACCESS);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.7);
    }

    @Test
    void resultNeverNull() {
        ClassificationResult result = classifier.classify(null);
        assertThat(result).isNotNull();
        assertThat(result.category()).isNotNull();
        assertThat(result.priority()).isNotNull();
    }
}
