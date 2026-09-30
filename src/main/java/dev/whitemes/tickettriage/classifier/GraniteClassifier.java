package dev.whitemes.tickettriage.classifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;
import dev.whitemes.tickettriage.domain.Category;
import dev.whitemes.tickettriage.domain.Priority;
import dev.whitemes.tickettriage.exception.ClassificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * AI-backed classifier that calls Granite 4:micro via Ollama + LangChain4j.
 *
 * Personal data is masked by {@link PersonalDataMasker} before the text is sent.
 * If the model returns an invalid response the exception propagates so that
 * {@link dev.whitemes.tickettriage.service.TicketService} can set the ticket to PENDING_HUMAN.
 */
@Service
@ConditionalOnProperty(name = "classifier.type", havingValue = "granite")
public class GraniteClassifier implements TicketClassifier {

    private static final Logger log = LoggerFactory.getLogger(GraniteClassifier.class);

    /**
     * System prompt: category definitions + 3 few-shot examples.
     * The model is instructed to reply with a single JSON object — nothing else.
     */
    static final String SYSTEM_PROMPT = """
            You are an IT support ticket classifier for a large financial organisation.
            Your only job is to read a ticket text and return a JSON object — no explanation, no markdown, just JSON.

            ## Categories (choose exactly one)
            - NETWORK   : connectivity issues, VPN, Wi-Fi, DNS, firewall, slow/no internet
            - HARDWARE  : physical device faults — computer, screen, keyboard, printer, peripheral
            - SOFTWARE  : application errors, crashes, installations, updates, OS issues
            - ACCESS    : password resets, account lock-outs, permission requests, SSO failures
            - SECURITY  : viruses, phishing, ransomware, suspected intrusions, data leaks
            - OTHER     : anything that does not fit the above categories

            ## Priority levels
            - CRITICAL : service fully down for many users or a security breach in progress
            - HIGH     : one user blocked, important deadline affected
            - MEDIUM   : degraded but workaround exists
            - LOW      : cosmetic issue or request with no urgency

            ## Priority rules
            Priority must reflect the actual impact described in the ticket.
            NEVER base priority solely on words like "urgent", "maximal", or "critical" written by the user.
            If the ticket does not describe a concrete impact, default to LOW or MEDIUM.

            ## Vagueness rule
            If the ticket text does not contain enough information to identify the problem,
            use category OTHER with a confidence below 0.5.

            ## Required JSON format
            {
              "category": "<one of the six values above>",
              "priority": "<CRITICAL | HIGH | MEDIUM | LOW>",
              "summary": "<one sentence, max 20 words>",
              "justification": "<one sentence explaining the category choice>",
              "confidence": <float between 0.0 and 1.0>
            }

            ## Examples

            Ticket: "Mon ordinateur ne démarre plus depuis ce matin, écran noir total."
            Response: {"category":"HARDWARE","priority":"HIGH","summary":"Ordinateur ne démarre plus, écran noir.","justification":"Panne matérielle : l'ordinateur ne s'allume pas.","confidence":0.92}

            Ticket: "Impossible de me connecter au VPN depuis ce matin, j'ai l'erreur 619."
            Response: {"category":"NETWORK","priority":"HIGH","summary":"Connexion VPN impossible, erreur 619.","justification":"Erreur VPN — problème réseau ou de configuration tunnel.","confidence":0.95}

            Ticket: "Mon compte est verrouillé après plusieurs tentatives de connexion incorrectes."
            Response: {"category":"ACCESS","priority":"HIGH","summary":"Compte verrouillé après tentatives échouées.","justification":"Verrouillage de compte — problème d'accès à résoudre par le helpdesk.","confidence":0.93}

            Ticket: "ça ne marche pas"
            Response: {"category":"OTHER","priority":"LOW","summary":"Demande vague sans détail sur le problème.","justification":"demande trop vague pour être classée","confidence":0.3}
            """;

    private final PersonalDataMasker masker;
    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public GraniteClassifier(
            PersonalDataMasker masker,
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model-name:granite4:micro}") String modelName,
            @Value("${ollama.timeout-seconds:60}") int timeoutSeconds) {
        requireText(baseUrl, "ollama.base-url");
        requireText(modelName, "ollama.model-name");
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("ollama.timeout-seconds must be > 0, got " + timeoutSeconds);
        }
        this.masker = masker;
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                // LangChain4j counts maxRetries as the TOTAL number of attempts (default 3).
                // One attempt only: on failure the ticket goes to the human queue instead.
                .maxRetries(1)
                .build();
    }

    /** Package-visible constructor for testing: accepts a stubbed {@link ChatLanguageModel}. */
    GraniteClassifier(PersonalDataMasker masker, ChatLanguageModel chatModel) {
        this.masker = masker;
        this.chatModel = chatModel;
    }

    @Override
    public ClassificationResult classify(String text) {
        String masked = masker.mask(text);

        long start = System.nanoTime();
        Response<AiMessage> response = chatModel.generate(
                List.of(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(masked)));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        String json = response.content().text().strip();
        log.info("[TRIAGE] Réponse brute du modèle en {} ms : {}", elapsedMs, json);

        return parseResult(json);
    }

    private ClassificationResult parseResult(String json) {
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

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
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
