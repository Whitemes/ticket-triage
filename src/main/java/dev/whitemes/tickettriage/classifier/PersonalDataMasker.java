package dev.whitemes.tickettriage.classifier;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Masks personal data (e-mail, phone, IBAN) in ticket text before it is sent to the model.
 * Applies replacements in order: e-mail → IBAN → phone.
 */
@Component
public class PersonalDataMasker {

    // Standard e-mail pattern
    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE);

    // French phone numbers: 0X XX XX XX XX, +33 X XX XX XX XX, or compact forms
    private static final Pattern PHONE = Pattern.compile(
            "(?:(?:\\+33|0033)[\\s.\\-]?[1-9]|0[1-9])" +
            "(?:[\\s.\\-]?\\d{2}){4}");

    // IBAN: 2 upper-case letters + 2 digits, then 2 to 7 blocks of 4 characters and an optional
    // 1-3 character remainder, blocks optionally separated by a space or a hyphen —
    // e.g. "FR76 3000 6000 0112 3456 7890 189" or "DE89370400440532013000".
    // Case-sensitive with word boundaries so IT codes such as SRV01, PC75, KB5034441 or Office365
    // are left intact. Matched before phone to prevent the phone pattern from consuming IBAN digits.
    private static final Pattern IBAN = Pattern.compile(
            "\\b[A-Z]{2}\\d{2}(?:[ \\-]?[A-Z0-9]{4}){2,7}(?:[ \\-]?[A-Z0-9]{1,3})?\\b");

    /**
     * Returns a copy of {@code text} with all personal data replaced by neutral markers.
     * Order: e-mail first, then IBAN (before phone so IBAN digits are not partially consumed),
     * then phone.
     *
     * @param text raw ticket text (may be null)
     * @return masked text, or empty string if input is null
     */
    public String mask(String text) {
        if (text == null) return "";
        String result = EMAIL.matcher(text).replaceAll("[EMAIL]");
        result = IBAN.matcher(result).replaceAll("[IBAN]");
        result = PHONE.matcher(result).replaceAll("[PHONE]");
        return result;
    }
}
