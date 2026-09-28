package dev.whitemes.tickettriage.classifier;

import dev.whitemes.tickettriage.domain.Category;
import dev.whitemes.tickettriage.domain.Priority;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Keyword-based classifier used as a demo fallback and in tests.
 * No AI required — deterministic, fast, and illustrates both routing paths.
 *
 * Rules (first match wins, case-insensitive). SECURITY is checked first so that an incident
 * mentioning the network or a password still reaches CRITICAL, hence human review:
 *   virus | malware | sécurité | security | phishing | hameçonnage | ransomware
 *     | rançongiciel | intrusion            → SECURITY, CRITICAL, confidence 0.88
 *   vpn | réseau | network   → NETWORK,   confidence 0.90
 *   mot de passe | password | accès | access → ACCESS, confidence 0.85
 *   imprimante | écran | clavier | hardware → HARDWARE, confidence 0.87
 *   logiciel | application | crash | software → SOFTWARE, confidence 0.86
 *   no match                               → OTHER,     confidence 0.40
 */
@Service
@ConditionalOnProperty(name = "classifier.type", havingValue = "fake", matchIfMissing = true)
public class FakeClassifier implements TicketClassifier {

    @Override
    public ClassificationResult classify(String text) {
        String lower = text == null ? "" : text.toLowerCase();

        if (matches(lower, "virus", "malware", "sécurité", "security", "phishing", "hameçonnage",
                "ransomware", "rançongiciel", "intrusion")) {
            return result(Category.SECURITY, Priority.CRITICAL,
                    "Incident de sécurité potentiel.", "Mots-clés sécurité/virus trouvés.", 0.88);
        }
        if (matches(lower, "vpn", "réseau", "network")) {
            return result(Category.NETWORK, Priority.HIGH,
                    "Problème réseau détecté.", "Mots-clés réseau/VPN trouvés.", 0.90);
        }
        if (matches(lower, "mot de passe", "password", "accès", "access")) {
            return result(Category.ACCESS, Priority.HIGH,
                    "Problème d'accès ou de mot de passe.", "Mots-clés accès/mot de passe trouvés.", 0.85);
        }
        if (matches(lower, "imprimante", "écran", "clavier", "hardware")) {
            return result(Category.HARDWARE, Priority.MEDIUM,
                    "Problème matériel signalé.", "Mots-clés matériel trouvés.", 0.87);
        }
        if (matches(lower, "logiciel", "application", "crash", "software")) {
            return result(Category.SOFTWARE, Priority.MEDIUM,
                    "Dysfonctionnement logiciel signalé.", "Mots-clés logiciel/crash trouvés.", 0.86);
        }

        // Unrecognised text → low confidence → human queue
        return result(Category.OTHER, Priority.LOW,
                "Catégorie indéterminée.", "Aucun mot-clé reconnu ou texte trop court.", 0.40);
    }

    private static boolean matches(String lower, String... keywords) {
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private static ClassificationResult result(Category cat, Priority prio,
                                               String summary, String justification,
                                               double confidence) {
        return new ClassificationResult(cat, prio, summary, justification, confidence);
    }
}
