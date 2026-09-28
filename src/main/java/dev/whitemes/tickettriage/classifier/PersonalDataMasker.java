package dev.whitemes.tickettriage.classifier;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Masks personal data (e-mail, phone, IBAN) in ticket text before it is sent to the model.
 * Applies replacements in order: e-mail → phone → IBAN.
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

    // IBAN: 2 letters + 2 digits + up to 30 alphanumeric chars (spaced or compact)
    private static final Pattern IBAN = Pattern.compile(
            "[A-Z]{2}\\d{2}[A-Z0-9]{4,30}(?:[\\s\\-][A-Z0-9]{4})*",
            Pattern.CASE_INSENSITIVE);

    /**
     * Returns a copy of {@code text} with all personal data replaced by neutral markers.
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
