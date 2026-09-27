# Plan — Phase 3 : README, diagramme Mermaid et instructions de lancement

## Vue d'ensemble

Finaliser la documentation du projet : un `README.md` complet avec un diagramme Mermaid illustrant l'architecture, les prérequis, les instructions de lancement en mode `fake` et en mode `granite`, et une note sur les limites et pistes d'amélioration (critère de jugement IBM Client Engineering : pensée critique).

Branche git : `phase-3-readme`

---

## Sous-tâche 3.1 — Diagramme d'architecture Mermaid

**Intention**
Visualiser en une seule image la structure en couches, les deux implémentations du classificateur, le flux de décision et la file humaine — pour que l'évaluateur comprenne l'architecture sans lire le code.

**Résultats attendus**
- Bloc `flowchart TD` dans le README décrivant :
  - Le formulaire web → `TicketController` → `TicketService`.
  - `TicketService` appelle `PersonalDataMasker` puis `TicketClassifier`.
  - Deux branches depuis `TicketClassifier` : `FakeClassifier` et `GraniteClassifier` (via Ollama).
  - Décision sur le seuil de confiance : ticket validé directement ou envoyé en file humaine.
  - Agent humain qui valide/corrige depuis la file humaine.
  - `TeamRouter` déduit l'équipe de la catégorie.
- Le diagramme se rend correctement sur GitHub.

**Étapes**
1. Rédiger le bloc Mermaid dans le README.
2. Vérifier la syntaxe (pas de guillemets doubles ni de parenthèses dans les labels de nœuds).

**Contexte**
- Architecture décrite dans AGENTS.md §Règles d'architecture.

**Statut** : [ ] en attente

---

## Sous-tâche 3.2 — Section Prérequis

**Intention**
Lister tout ce dont un évaluateur a besoin pour faire tourner le projet en partant de zéro.

**Résultats attendus**
- Section `## Prérequis` dans le README avec :
  - Java 21 (JDK).
  - Maven 3.9+.
  - (Mode granite uniquement) Ollama installé et modèle `granite4:micro` téléchargé : commande `ollama pull granite4:micro`.
  - Aucune connexion cloud, aucune clé API.

**Étapes**
1. Rédiger la section dans le README.

**Statut** : [ ] en attente

---

## Sous-tâche 3.3 — Section Lancement

**Intention**
Permettre à l'évaluateur de lancer la démo en deux commandes, que ce soit en mode fake (sans GPU) ou en mode granite (avec Ollama).

**Résultats attendus**
- Section `## Lancement` avec deux sous-sections :

**Mode FakeClassifier (aucune dépendance IA)**
```bash
mvn spring-boot:run
```
Accès sur `http://localhost:8080`.

**Mode GraniteClassifier (Ollama requis)**
```bash
# 1. Démarrer Ollama
ollama serve
# 2. Lancer l'application avec le profil granite
mvn spring-boot:run -Dspring-boot.run.arguments="--classifier.type=granite"
```
Accès sur `http://localhost:8080`.

- Section `## Tests` :
```bash
mvn verify
```
Résultat attendu : tous les tests passent, y compris `ticket_vpn_classifie_en_network`.

**Étapes**
1. Rédiger les deux sous-sections dans le README.

**Statut** : [ ] en attente

---

## Sous-tâche 3.4 — Section Cas d'usage et flux de décision

**Intention**
Expliquer en prose (3–5 phrases) le flux d'un ticket du formulaire jusqu'à la file humaine, pour contextualiser le diagramme.

**Résultats attendus**
- Section `## Flux de traitement` décrivant :
  - Soumission du texte libre.
  - Masquage des données personnelles.
  - Appel au classificateur (fake ou granite).
  - Décision confiance : validation automatique ou file humaine.
  - Correction/validation par l'agent humain.
  - Attribution de l'équipe par `TeamRouter`.

**Étapes**
1. Rédiger la section en 4–6 phrases claires.

**Statut** : [ ] en attente

---

## Sous-tâche 3.5 — Section Limites et pistes d'amélioration

**Intention**
Démontrer la pensée critique attendue par IBM Client Engineering (critère explicite dans AGENTS.md).

**Résultats attendus**
- Section `## Limites et pistes d'amélioration` couvrant au moins :
  - Limite du petit modèle local (granite4:micro) sur des tickets ambigus.
  - Absence de persistance entre redémarrages (H2 en mémoire).
  - Absence d'authentification pour l'interface de validation humaine.
  - Piste : fine-tuning ou few-shot plus riche pour améliorer la précision.
  - Piste : base de données persistante (PostgreSQL) pour un vrai déploiement.
  - Piste : tableau de bord de suivi du taux de validation humaine.

**Étapes**
1. Rédiger la section (liste à puces).

**Statut** : [ ] en attente

---

## Sous-tâche 3.6 — Validation finale et commit

**Intention**
S'assurer que le README est lisible, que le diagramme se rend sur GitHub, et que le projet est propre pour la restitution.

**Résultats attendus**
- `mvn verify` passe une dernière fois.
- Le README s'affiche correctement dans l'aperçu GitHub (diagramme visible).
- Aucun secret dans le dépôt (`git grep -i password`, `git grep -i api.key`).
- Commit propre sur `phase-3-readme`, fusionné dans `main`.

**Étapes**
1. Relire le README complet.
2. Lancer `mvn verify`.
3. Vérifier l'absence de secrets avec `git grep`.
4. Commit et push de `phase-3-readme`, fusion dans `main`.

**Statut** : [ ] en attente

---

## Critère de fin de phase 3

- `mvn verify` passe sans erreur.
- Le README contient le diagramme Mermaid, les prérequis, les instructions de lancement, le flux de traitement et la section limites.
- Le dépôt `main` est propre, sans secret, avec un historique de commits clair par phase.
- Le projet est prêt pour la restitution du lundi 28 septembre.

## Fichiers créés ou modifiés par cette phase

| Fichier | Rôle |
|---|---|
| `README.md` | Documentation complète du projet |
