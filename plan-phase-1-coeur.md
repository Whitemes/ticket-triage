# Plan — Phase 1 : Cœur de l'application avec FakeClassifier

## Vue d'ensemble

Construire le squelette fonctionnel complet du système de tri de tickets, entièrement testable et démontrable **sans** modèle IA. La seule implémentation active de `TicketClassifier` est `FakeClassifier`, qui applique des règles de mots-clés simples. Tout le reste — domaine, service, routage vers la file humaine, validation/correction, interface web — est réel et opérationnel.

Branche git : `phase-1-coeur`

---

## Ajustements appliqués au plan initial

1. **Paquet racine** : `dev.whitemes.tickettriage` (au lieu de `com.example`).
2. **`Priority` est un enum** : `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` — liste fermée, comme `Category`.
3. **Trois statuts de ticket** : `ROUTED` (tri automatique accepté, confiance ≥ seuil), `PENDING_HUMAN` (confiance < seuil ou résultat invalide), `VALIDATED` (confirmé ou corrigé par un agent humain).
4. **`FakeClassifier` avec règles de mots-clés** : pas de retour fixe unique — reconnaît des mots-clés pour illustrer les deux chemins (file automatique et file humaine) lors de la démo de secours :
   - `"vpn"`, `"réseau"`, `"network"` → `NETWORK`, confiance `0.9`
   - `"mot de passe"`, `"password"`, `"accès"`, `"access"` → `ACCESS`, confiance `0.85`
   - `"virus"`, `"malware"`, `"sécurité"`, `"security"` → `SECURITY`, confiance `0.88`
   - `"imprimante"`, `"écran"`, `"clavier"`, `"hardware"` → `HARDWARE`, confiance `0.87`
   - `"logiciel"`, `"application"`, `"crash"`, `"software"` → `SOFTWARE`, confiance `0.86`
   - Texte court (< 10 mots) ou aucun mot-clé reconnu → `OTHER`, confiance `0.4` → part en file humaine

---

## Sous-tâche 1.1 — Structure Maven et squelette Spring Boot

**Intention**
Mettre en place le projet Maven standard avec Spring Boot 3, Java 21, H2 et Thymeleaf, de façon à pouvoir compiler et lancer `mvn verify` dès le départ.

**Résultats attendus**
- `pom.xml` présent avec les dépendances `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-data-jpa`, `h2`, `junit-5` (via `spring-boot-starter-test`).
- `mvn verify` passe (aucun test encore, mais le build compile).
- La classe principale `TicketTriageApplication` démarre sans erreur.

**Étapes**
1. Créer `pom.xml` (groupId `dev.whitemes`, artifactId `ticket-triage`, Java 21, Spring Boot 3.x).
2. Créer `src/main/java/dev/whitemes/tickettriage/TicketTriageApplication.java`.
3. Créer `src/main/resources/application.properties` avec H2 en mémoire et console H2 activée.
4. Vérifier `mvn verify`.

**Contexte**
- Stack imposée : Java 21, Spring Boot 3, Maven, H2, Thymeleaf (AGENTS.md §Stack).
- Pas de LangChain4j dans cette phase (ajouté en phase 2).

**Statut** : [x] terminé

---

## Sous-tâche 1.2 — Domaine : enums `Category` et `Priority`, entité `Ticket`, table de correspondance

**Intention**
Définir les listes fermées des catégories et priorités métier, la règle de routage vers l'équipe de destination, et l'entité JPA persistée.

**Résultats attendus**
- Enum `Category` : `NETWORK`, `HARDWARE`, `SOFTWARE`, `ACCESS`, `SECURITY`, `OTHER`.
- Enum `Priority` : `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
- Classe `TeamRouter` contenant une `Map<Category, String>` qui associe chaque catégorie à un nom d'équipe.
- Entité JPA `Ticket` avec les champs : `id`, `rawText`, `category`, `priority`, `team`, `summary`, `justification`, `confidence`, `status` (enum : `ROUTED`, `PENDING_HUMAN`, `VALIDATED`), `createdAt`.
- Repository JPA `TicketRepository`.

**Étapes**
1. Créer `Category.java` (enum, package `domain`).
2. Créer `Priority.java` (enum, package `domain`).
3. Créer `TeamRouter.java` avec la `Map` statique catégorie → équipe.
4. Créer `Ticket.java` (entité JPA).
5. Créer `TicketRepository.java` (interface `JpaRepository`).

**Contexte**
- AGENTS.md : « Les catégories forment une liste fermée (enum Java) » et « L'équipe de destination n'est jamais choisie par le modèle ».

**Statut** : [x] terminé

---

## Sous-tâche 1.3 — Interface `TicketClassifier` et `FakeClassifier`

**Intention**
Définir le contrat que tout classificateur doit respecter, et en fournir une implémentation à base de mots-clés utilisable sans IA pour les tests et la démo de secours.

**Résultats attendus**
- Interface `TicketClassifier` avec une méthode `ClassificationResult classify(String text)`.
- Record `ClassificationResult` portant : `category` (`Category`), `priority` (`Priority`), `summary`, `justification`, `confidence` (double 0–1).
- `FakeClassifier` : implémentation avec règles de mots-clés (voir §Ajustements). Les textes courts ou sans mot-clé produisent `OTHER` / confiance `0.4` → bascule en `PENDING_HUMAN`.
- Propriété `classifier.type=fake` dans `application.properties` ; un `@ConditionalOnProperty` sélectionne l'implémentation.

**Étapes**
1. Créer `ClassificationResult.java` (record).
2. Créer `TicketClassifier.java` (interface).
3. Créer `FakeClassifier.java` avec la logique de mots-clés.
4. Configurer la sélection par propriété.

**Contexte**
- AGENTS.md : « Une interface TicketClassifier avec deux implémentations ».

**Statut** : [x] terminé

---

## Sous-tâche 1.4 — Service `TicketService` : soumettre, trier, file humaine

**Intention**
Orchestrer le flux complet : recevoir le texte brut, appeler le classificateur, persister le ticket, et aiguiller selon le statut.

**Résultats attendus**
- `TicketService` avec une méthode `submit(String rawText) : Ticket`.
- Si `confidence >= seuil` : ticket enregistré avec statut `ROUTED`.
- Si `confidence < seuil` ou résultat invalide : statut `PENDING_HUMAN`.
- Méthode `getPendingTickets() : List<Ticket>` qui retourne la file humaine.
- Méthode `validate(Long id, Category correctedCategory, Priority correctedPriority) : Ticket` pour qu'un agent confirme ou corrige → statut `VALIDATED`.

**Étapes**
1. Créer `TicketService.java` avec injection de `TicketClassifier`, `TicketRepository`, `TeamRouter`.
2. Implémenter `submit`, `getPendingTickets`, `validate`.
3. Lire le seuil depuis `application.properties` via `@Value`.

**Contexte**
- AGENTS.md : « Si la réponse du modèle est invalide ou si la confiance est sous le seuil, le ticket part dans la file de validation humaine ».

**Statut** : [x] terminé

---

## Sous-tâche 1.5 — Contrôleurs et vues Thymeleaf

**Intention**
Offrir une interface web minimale permettant de soumettre un ticket, de voir la file humaine et de valider/corriger un ticket.

**Résultats attendus**
- `TicketController` avec les routes :
  - `GET /` — formulaire de soumission.
  - `POST /tickets` — soumet le ticket et redirige vers la page de résultat.
  - `GET /tickets/{id}` — détail d'un ticket.
  - `GET /human-queue` — liste des tickets `PENDING_HUMAN`.
  - `POST /human-queue/{id}/validate` — confirme ou corrige la catégorie et la priorité → statut `VALIDATED`.
- Templates Thymeleaf correspondants (pas de CSS élaboré requis).

**Étapes**
1. Créer `TicketController.java`.
2. Créer les templates : `index.html`, `ticket-result.html`, `human-queue.html`, `ticket-detail.html`.

**Contexte**
- Stack : Thymeleaf servi par Spring (AGENTS.md §Stack).

**Statut** : [x] terminé

---

## Sous-tâche 1.6 — Tests unitaires et d'intégration Phase 1

**Intention**
Valider le domaine et le service avec `FakeClassifier`, sans dépendance à un modèle IA.

**Résultats attendus**
- `TeamRouterTest` : chaque catégorie de l'enum est associée à une équipe non nulle.
- `FakeClassifierTest` :
  - Un texte contenant `"vpn"` → catégorie `NETWORK`, confiance ≥ 0.7.
  - Un texte court (< 10 mots) → catégorie `OTHER`, confiance < 0.7.
- `TicketServiceTest` (test unitaire avec mock) :
  - Un ticket soumis avec confiance ≥ seuil est persisté avec statut `ROUTED`.
  - Un ticket soumis avec confiance < seuil est persisté avec statut `PENDING_HUMAN`.
  - `validate()` met à jour la catégorie, la priorité, l'équipe et le statut → `VALIDATED`.
- `TicketControllerIT` (test d'intégration Spring Boot, H2) :
  - `POST /tickets` crée bien un ticket en base.
  - `GET /human-queue` renvoie HTTP 200.
- `mvn verify` passe en entier.

**Étapes**
1. Créer les classes de test dans `src/test/java`.
2. Utiliser `@SpringBootTest` + `MockMvc` pour les tests web.
3. Utiliser Mockito pour isoler `TicketService` du vrai classificateur dans les tests unitaires.

**Statut** : [x] terminé

---

## Critère de fin de phase 1

- `mvn verify` passe sans erreur ni test ignoré.
- L'application démarre avec `mvn spring-boot:run` et le formulaire est accessible sur `http://localhost:8080`.
- La file humaine affiche les tickets en attente.
- Un commit clair est poussé sur la branche `phase-1-coeur`, puis fusionné dans `main`.

## Fichiers créés par cette phase

| Fichier | Rôle |
|---|---|
| `pom.xml` | Configuration Maven |
| `TicketTriageApplication.java` | Point d'entrée Spring Boot |
| `application.properties` | Config H2, seuil de confiance, type de classificateur |
| `Category.java` | Enum des catégories (NETWORK, HARDWARE, SOFTWARE, ACCESS, SECURITY, OTHER) |
| `Priority.java` | Enum des priorités (LOW, MEDIUM, HIGH, CRITICAL) |
| `TeamRouter.java` | Table catégorie → équipe |
| `Ticket.java` | Entité JPA (statuts : ROUTED, PENDING_HUMAN, VALIDATED) |
| `TicketRepository.java` | Repository JPA |
| `ClassificationResult.java` | Record de résultat de classification |
| `TicketClassifier.java` | Interface du classificateur |
| `FakeClassifier.java` | Implémentation par mots-clés (démo et tests) |
| `TicketService.java` | Logique métier principale |
| `TicketController.java` | Contrôleur web |
| `index.html` / `ticket-result.html` / `human-queue.html` / `ticket-detail.html` | Vues Thymeleaf |
| `TeamRouterTest.java` | Tests de la table de routage |
| `FakeClassifierTest.java` | Tests du classificateur factice |
| `TicketServiceTest.java` | Tests unitaires du service |
| `TicketControllerIT.java` | Tests d'intégration web |
