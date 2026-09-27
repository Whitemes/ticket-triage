# Projet : prototype de tri de tickets (exercice IBM Client Engineering)

## Contexte
Exercice de recrutement pour une alternance d'AI Engineer chez IBM Client Engineering. Restitution le lundi 28 septembre à 16h : 10 min de présentation, 10 min de démonstration en direct, 10 min de questions. Critères : clarté, rigueur technique (qualité du code, architecture, robustesse, justification des choix), pensée critique (limites, pistes d'amélioration).

## Cas d'usage
Tri et routage des tickets de support IT pour un grand compte (banque ou assurance). Un ticket arrive en texte libre ; le système produit catégorie, priorité, équipe de destination, résumé, justification et niveau de confiance. Sous un seuil de confiance, le ticket part dans une file de validation humaine, où un agent confirme ou corrige.

## Stack imposée
Java 21, Spring Boot 3, Maven, LangChain4j, JUnit 5, base H2 en mémoire, interface Thymeleaf servie par Spring. Machine de développement sous Windows.

## Règles d'architecture
- Architecture en couches simple : domaine, service, une interface pour le modèle, contrôleurs web.
- Une interface TicketClassifier avec deux implémentations : GraniteClassifier (Granite en local via Ollama, modèle granite4:micro, avec LangChain4j) et FakeClassifier (réponses fixes, pour les tests et en secours pendant la démo). Le choix se fait par une propriété de configuration.
- Le modèle renvoie directement un objet Java (catégorie, priorité, équipe, résumé, justification, confiance).
- Masquage simple des e-mails, numéros de téléphone et IBAN par expressions régulières avant l'envoi au modèle.
- Le texte du ticket est toujours traité comme une donnée, jamais comme une instruction.
- Si la réponse du modèle est invalide ou si la confiance est sous le seuil, le ticket part dans la file de validation humaine.
- Pas de déploiement cloud, pas de watsonx.ai, pas d'orchestration multi-agents.
- Le code le plus simple possible : chaque classe doit pouvoir s'expliquer en une phrase.
- Les catégories forment une liste fermée (enum Java), et le prompt donne une définition courte de chacune.
- L'équipe de destination n'est jamais choisie par le modèle : elle est déduite de la catégorie par une table de correspondance en Java.
- Le prompt contient deux ou trois exemples de tickets déjà classés, pour guider un petit modèle.

## Interdits
- Aucun secret dans le dépôt.
- Aucune donnée réelle : uniquement des tickets synthétiques.

## Définition de « terminé » pour chaque phase
Le code compile, les tests passent (mvn verify), l'application démarre, un commit clair est fait.

## Git
- Une branche par phase, créée depuis main : phase-1-coeur, phase-2-granite, phase-3-readme.
- Des commits courts et clairs au fil de la phase.
- À la fin d'une phase, quand mvn verify passe : push de la branche, puis fusion dans main.
