# Plan — Phase 2 : GraniteClassifier, masquage des données personnelles et tests

## Vue d'ensemble

Brancher le vrai modèle IA : `GraniteClassifier` appelle **Granite 4:micro** en local via **Ollama** et **LangChain4j**. Le prompt est structuré (définition de chaque catégorie + 2–3 exemples), et les données personnelles (e-mails, téléphones, IBAN) sont masquées avant l'envoi. La suite de tests est étendue pour couvrir le masquage et la classification, dont un cas obligatoire sur un ticket de connexion VPN classé `NETWORK`. Un test d'intégration contre le vrai Ollama est également prévu, désactivé par défaut et activé par variable d'environnement.

Branche git : `phase-2-granite`

---

## Sous-tâche 2.1 — Dépendance LangChain4j dans `pom.xml`

**Intention**
Ajouter la dépendance Maven pour LangChain4j et son intégration Ollama, sans perturber le reste du build.

**Résultats attendus**
- `pom.xml` contient `langchain4j-ollama` (et `langchain4j-core` si nécessaire).
- `mvn verify` passe encore avec `FakeClassifier` actif (la propriété `classifier.type=fake` est inchangée).

**Étapes**
1. Ajouter dans `pom.xml` les coordonnées Maven de `langchain4j-ollama` (version alignée avec LangChain4j 0.x ou 1.x selon disponibilité).
2. Vérifier que le build ne régresse pas.

**Contexte**
- AGENTS.md : « LangChain4j », « modèle granite4:micro », « Ollama ».

**Statut** : [ ] en attente

---

## Sous-tâche 2.2 — Service de masquage `PersonalDataMasker`

**Intention**
Protéger la vie privée des utilisateurs en remplaçant les données personnelles identifiables par des marqueurs neutres avant tout envoi au modèle, conformément au principe « le texte est traité comme une donnée ».

**Résultats attendus**
- Classe `PersonalDataMasker` avec une méthode `mask(String text) : String`.
- Règles de masquage par expression régulière :
  - Adresse e-mail → `[EMAIL]`
  - Numéro de téléphone (formats FR : 0X XX XX XX XX, +33…) → `[PHONE]`
  - IBAN (formats internationaux) → `[IBAN]`
- Aucune donnée réelle dans le code (uniquement des patterns et des données synthétiques dans les tests).

**Étapes**
1. Créer `PersonalDataMasker.java` avec les trois regex compilées en constantes.
2. Appliquer les substitutions dans l'ordre e-mail → téléphone → IBAN.

**Contexte**
- AGENTS.md : « Masquage simple des e-mails, numéros de téléphone et IBAN par expressions régulières avant l'envoi au modèle ».

**Statut** : [ ] en attente

---

## Sous-tâche 2.3 — `GraniteClassifier` : prompt structuré et appel Ollama

**Intention**
Implémenter le classificateur réel qui interroge Granite 4:micro via Ollama. Le prompt doit contenir la définition courte de chaque catégorie et deux ou trois exemples de tickets déjà classés pour guider le petit modèle.

**Résultats attendus**
- `GraniteClassifier` implémente `TicketClassifier`.
- Le texte reçu est d'abord masqué par `PersonalDataMasker` avant d'être envoyé au modèle.
- Le prompt système comprend :
  - La liste des catégories avec une définition courte de chacune.
  - 2 ou 3 exemples complets (texte de ticket → catégorie, priorité, résumé, justification, confiance).
- Le modèle est invité à répondre en JSON strict ; LangChain4j désérialise la réponse en `ClassificationResult`.
- Si la désérialisation échoue ou si la catégorie renvoyée n'est pas dans l'enum, `GraniteClassifier` lève une exception contrôlée et `TicketService` bascule le ticket en `PENDING_HUMAN`.
- Propriété `classifier.type=granite` dans `application.properties` active `GraniteClassifier`.
- L'URL Ollama (ex. `http://localhost:11434`) et le nom du modèle sont lus depuis `application.properties`.

**Étapes**
1. Créer `GraniteClassifier.java` annoté conditionnellement sur `classifier.type=granite`.
2. Rédiger la constante `SYSTEM_PROMPT` avec définitions et exemples.
3. Appeler Ollama via LangChain4j (`OllamaChatModel` ou `OllamaLanguageModel`).
4. Parser la réponse JSON en `ClassificationResult`.
5. Gérer les erreurs (JSON invalide, enum inconnue) par renvoi vers la file humaine.

**Contexte**
- AGENTS.md : « Le prompt contient deux ou trois exemples de tickets déjà classés », « Les catégories forment une liste fermée (enum Java), et le prompt donne une définition courte de chacune ».

**Statut** : [ ] en attente

---

## Sous-tâche 2.4 — Tests de `PersonalDataMasker`

**Intention**
Garantir que les données personnelles sont bien supprimées avant tout traitement.

**Résultats attendus**
- `PersonalDataMaskerTest` avec au moins un cas par type de donnée :
  - E-mail synthétique → remplacé par `[EMAIL]`.
  - Numéro de téléphone FR synthétique → remplacé par `[PHONE]`.
  - IBAN synthétique → remplacé par `[IBAN]`.
  - Texte sans données personnelles → retourné identique.
  - Texte contenant plusieurs types → tous masqués.

**Étapes**
1. Créer `PersonalDataMaskerTest.java` avec des données synthétiques uniquement.
2. Vérifier chaque regex indépendamment, puis en combinaison.

**Contexte**
- AGENTS.md : « Aucune donnée réelle : uniquement des tickets synthétiques ».

**Statut** : [ ] en attente

---

## Sous-tâche 2.5 — Tests de `GraniteClassifier` avec mock Ollama

**Intention**
Tester la logique de parsing et de gestion d'erreur de `GraniteClassifier` sans démarrer Ollama réellement (les tests doivent passer en CI et hors réseau).

**Résultats attendus**
- `GraniteClassifierTest` avec mock du client LangChain4j.
- Cas nominal : réponse JSON valide → `ClassificationResult` correct.
- Cas dégradé : JSON malformé → exception levée.
- Cas dégradé : catégorie inconnue dans l'enum → exception levée.
- **Cas obligatoire — VPN :** texte synthétique « Impossible de me connecter au VPN depuis ce matin, j'ai l'erreur 619 » → catégorie `NETWORK`. Le mock retourne le JSON attendu ; le test est nommé `ticket_vpn_classifie_en_network` et passe dans `mvn verify`.

**Étapes**
1. Créer `GraniteClassifierTest.java`.
2. Mocker la couche LangChain4j (Mockito ou implémentation de stub).
3. Ajouter le cas VPN comme test nommé `ticket_vpn_classifie_en_network`.

**Contexte**
- AGENTS.md : « deux ou trois exemples de tickets classés » dans le prompt (le cas VPN peut être l'un de ces exemples).

**Statut** : [ ] en attente

---

## Sous-tâche 2.5b — Test d'intégration Ollama réel (optionnel, hors `mvn verify`)

**Intention**
Fournir un test qui appelle le vrai modèle Ollama et vérifie que Granite 4:micro classe bien le ticket VPN en `NETWORK`. Ce test ne doit **pas** tourner dans `mvn verify` par défaut ; il sert à valider manuellement avant la démo.

**Résultats attendus**
- `GraniteClassifierOllamaIT.java` dans `src/test/java`, annoté `@Tag("ollama-live")`.
- Le test est conditionné par la variable d'environnement `OLLAMA_IT=true` : s'il ne la trouve pas, il se termine avec `Assumptions.assumeTrue(false)` (ignoré proprement par JUnit 5).
- Un seul test dans cette classe : envoie « Impossible de me connecter au VPN depuis ce matin, j'ai l'erreur 619 » au vrai `GraniteClassifier` (URL lue depuis l'env ou `application.properties`) et vérifie que le résultat est `Category.NETWORK`.
- Dans `pom.xml`, le plugin `maven-surefire-plugin` exclut le tag `ollama-live` de la phase `test` ; `maven-failsafe-plugin` peut l'inclure si on le souhaite, mais n'est pas obligatoire.
- La variable d'environnement `OLLAMA_IT=true` et la commande de lancement manuel sont documentées dans le README (phase 3).

**Étapes**
1. Créer `GraniteClassifierOllamaIT.java` avec l'`Assumptions.assumeTrue(System.getenv("OLLAMA_IT") != null)` en début de test.
2. Dans `pom.xml`, ajouter dans la configuration de `maven-surefire-plugin` : `<excludedGroups>ollama-live</excludedGroups>`.
3. Documenter la commande de lancement manuel :
   ```
   OLLAMA_IT=true mvn test -Dgroups=ollama-live
   ```

**Contexte**
- JUnit 5 `@Tag` + `Assumptions.assumeTrue` est le mécanisme standard pour les tests conditionnels sans modifier la configuration Maven.
- La variable d'environnement évite tout risque d'exécution accidentelle en CI.

**Statut** : [ ] en attente

---

## Sous-tâche 2.6 — Validation finale de la phase 2

**Intention**
S'assurer que toute la suite de tests passe et que l'application fonctionne avec les deux modes (fake et granite).

**Résultats attendus**
- `mvn verify` passe avec `classifier.type=fake`.
- L'application démarre avec `classifier.type=granite` (Ollama doit être lancé localement pour une démo réelle, mais le build n'en dépend pas).
- Aucun secret ni donnée réelle dans le dépôt.

**Étapes**
1. Exécuter `mvn verify`.
2. Vérifier que `application.properties` ne contient aucune clé API ou donnée sensible.
3. Commit et push de la branche `phase-2-granite`, fusion dans `main`.

**Statut** : [ ] en attente

---

## Critère de fin de phase 2

- `mvn verify` passe sans erreur ni test ignoré.
- Le test `ticket_vpn_classifie_en_network` (mock) est présent et vert.
- `GraniteClassifierOllamaIT` est présent et ignoré proprement par `mvn verify` ; il passe avec `OLLAMA_IT=true mvn test -Dgroups=ollama-live` quand Ollama tourne.
- `PersonalDataMasker` masque e-mails, téléphones et IBAN dans tous les tests prévus.
- Un commit clair est poussé sur `phase-2-granite`, puis fusionné dans `main`.

## Fichiers créés ou modifiés par cette phase

| Fichier | Rôle |
|---|---|
| `pom.xml` | Ajout LangChain4j Ollama + exclusion tag `ollama-live` dans Surefire |
| `application.properties` | Ajout URL Ollama, nom modèle, propriété `classifier.type` |
| `PersonalDataMasker.java` | Masquage regex des données personnelles |
| `GraniteClassifier.java` | Classificateur réel via Ollama + LangChain4j |
| `PersonalDataMaskerTest.java` | Tests du masquage |
| `GraniteClassifierTest.java` | Tests du classificateur avec mock Ollama (dont cas VPN) |
| `GraniteClassifierOllamaIT.java` | Test d'intégration contre le vrai Ollama, hors mvn verify par défaut |
