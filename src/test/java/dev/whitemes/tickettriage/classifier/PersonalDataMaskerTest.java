package dev.whitemes.tickettriage.classifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link PersonalDataMasker} — all data is synthetic. */
class PersonalDataMaskerTest {

    private PersonalDataMasker masker;

    @BeforeEach
    void setUp() {
        masker = new PersonalDataMasker();
    }

    // --- E-mail ---

    @Test
    void email_is_replaced_by_marker() {
        String result = masker.mask("Contactez jean.dupont@example.com pour plus d'infos.");
        assertThat(result).doesNotContain("jean.dupont@example.com")
                .contains("[EMAIL]");
    }

    @Test
    void multiple_emails_are_all_replaced() {
        String result = masker.mask("De : alice@test.fr À : bob@test.fr");
        assertThat(result).doesNotContain("alice@test.fr").doesNotContain("bob@test.fr")
                .containsPattern("\\[EMAIL\\].*\\[EMAIL\\]");
    }

    // --- Phone ---

    @Test
    void french_phone_10digits_is_replaced() {
        String result = masker.mask("Appelez le 06 12 34 56 78 s'il vous plaît.");
        assertThat(result).doesNotContain("06 12 34 56 78")
                .contains("[PHONE]");
    }

    @Test
    void french_phone_plus33_is_replaced() {
        String result = masker.mask("Mon numéro est +33 6 12 34 56 78.");
        assertThat(result).doesNotContain("+33 6 12 34 56 78")
                .contains("[PHONE]");
    }

    // --- IBAN ---

    @Test
    void iban_compact_is_replaced_by_marker() {
        // Synthetic FR IBAN (not a real account)
        String result = masker.mask("Mon IBAN est FR7614508059050000000000000.");
        assertThat(result).doesNotContain("FR7614508059050000000000000")
                .contains("[IBAN]");
    }

    @Test
    void iban_spaced_is_replaced_and_no_digit_leaks_to_model() {
        // Synthetic spaced FR IBAN — format used by most French banks
        var spaced = "FR76 3000 6000 0112 3456 7890 189";
        String result = masker.mask("Voici mon IBAN : " + spaced + " merci.");
        assertThat(result)
                .as("aucun chiffre de l'IBAN ne doit subsister dans le texte masqué")
                .doesNotContainPattern("3000|6000|0112|3456|7890|189")
                .contains("[IBAN]");
    }

    @Test
    void spaced_iban_is_replaced_and_surrounding_text_is_kept() {
        assertThat(masker.mask("Voici mon IBAN : FR76 3000 6000 0112 3456 7890 189 merci."))
                .isEqualTo("Voici mon IBAN : [IBAN] merci.");
    }

    // --- No personal data ---

    @Test
    void text_without_personal_data_is_returned_unchanged() {
        var text = "Mon imprimante HP ne fonctionne plus depuis la mise à jour.";
        assertThat(masker.mask(text)).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Le serveur SRV01 ne répond plus",
            "Mon poste PC75 ne se connecte plus au VPN",
            "Suite à la mise à jour KB5034441 le VPN ne marche plus",
            "Ticket INC0012345 toujours pas résolu",
            "Office365 plante au démarrage",
            "Win11 ne démarre plus"
    })
    void technical_codes_are_not_masked(String text) {
        assertThat(masker.mask(text)).isEqualTo(text);
    }

    // --- Null input ---

    @Test
    void null_input_returns_empty_string() {
        assertThat(masker.mask(null)).isEqualTo("");
    }

    // --- Multiple types in one text ---

    @Test
    void all_personal_data_types_are_masked_in_combined_text() {
        var text = "Contact : marie@banque.fr, tél 01 23 45 67 89, " +
                "IBAN DE89370400440532013000.";
        String result = masker.mask(text);

        assertThat(result)
                .doesNotContain("marie@banque.fr")
                .doesNotContain("01 23 45 67 89")
                .doesNotContain("DE89370400440532013000")
                .contains("[EMAIL]")
                .contains("[PHONE]")
                .contains("[IBAN]");
    }
}
