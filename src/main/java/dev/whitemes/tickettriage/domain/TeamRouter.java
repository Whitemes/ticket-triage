package dev.whitemes.tickettriage.domain;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Maps each {@link Category} to the responsible support team.
 * The team is never chosen by the classifier — this table is the single source of truth.
 */
@Component
public class TeamRouter {

    private static final Map<Category, String> ROUTING = new EnumMap<>(Category.class);

    static {
        ROUTING.put(Category.NETWORK,  "Équipe Réseau");
        ROUTING.put(Category.HARDWARE, "Équipe Matériel");
        ROUTING.put(Category.SOFTWARE, "Équipe Logiciel");
        ROUTING.put(Category.ACCESS,   "Équipe Accès & IAM");
        ROUTING.put(Category.SECURITY, "Équipe Sécurité");
        ROUTING.put(Category.OTHER,    "Équipe Support Général");
    }

    /** Returns the team name for the given category. Never returns null. */
    public String route(Category category) {
        return ROUTING.getOrDefault(category, "Équipe Support Général");
    }
}
