# Documentation - Simulation Centre d'Appels VANAD

## Vue d'ensemble

Ce système implémente une **simulation avec replay temporel fidèle** du centre d'appel VANAD. Il reconstitue exactement l'état du système à chaque moment selon les données historiques pour générer un dataset d'entraînement optimisé pour les réseaux de neurones artificiels (ANN).

### Objectif Principal
Générer un dataset d'entraînement au format exact : **x = (T, qT, ℓ, t, s, P_LES, P_Avg-LES) + W** pour l'entraînement d'un modèle de prédiction des temps d'attente.

## Architecture du Système

### Classes Principales

#### 1. `CallCenterSimulation` (Classe principale)
**Responsabilité** : Orchestration du processus de replay et génération du dataset.

**Méthodes clés :**
- `runHistoricalReplay()` : Point d'entrée principal
- `loadAndPrepareData()` : Chargement et filtrage des données
- `executeEventDrivenReplay()` : Exécution du replay événementiel
- `exportTrainingDataset()` : Export au format CSV spécifié

#### 2. `SystemReplayEngine` (Moteur de replay)
**Responsabilité** : Reconstitution fidèle de l'état du centre d'appels à chaque instant.

**Fonctionnalités :**
- Gestion des files d'attente par service
- Suivi en temps réel des états des agents
- Calcul des prédicteurs de base (LES, Avg-LES)
- Traitement événementiel des appels

#### 3. `AgentState` (État des agents)
**Responsabilité** : Modélisation fidèle de la disponibilité des agents.

**Propriétés :**
- `skills` : Compétences par service
- `busyWithCallUntil` : Occupation jusqu'à un moment donné
- `unavailableUntil` : Indisponibilité programmée

## Flux de Traitement

### Phase 1 : Préparation des Données
```
1. Chargement des appels historiques
2. Identification des services principaux (top 8 par volume)
3. Filtrage des appels sur les services sélectionnés
4. Tri chronologique des événements
5. Chargement des activités des agents
```

### Phase 2 : Initialisation du Replay
```
1. Configuration du moteur de replay
2. Initialisation des compétences agents (extraites des appels traités)
3. Création des structures de données (files, états)
```

### Phase 3 : Replay Événementiel
```
Pour chaque appel dans l'ordre chronologique :
  1. Capturer l'état système AVANT l'arrivée
  2. Calculer les prédicteurs (LES, Avg-LES)
  3. Enregistrer l'échantillon d'entraînement
  4. Mettre à jour l'état système avec l'événement
```

### Phase 4 : Export du Dataset
```
Format de sortie : training_dataset_vanad_ann.csv
Structure : T,qT,l1,l2,l3,l4,l5,t_hour,t_day_of_week,s,P_LES,P_Avg_LES,W
```

## Structure du Vecteur d'État

### Variables d'Entrée (x)
| Variable | Description | Type | Exemple |
|----------|-------------|------|---------|
| **T** | Type de service (encodé) | Integer | 1-8 |
| **qT** | Longueur file service T | Integer | 15 |
| **ℓ** | Longueurs autres files (top 5) | Integer[5] | [8,12,3,0,1] |
| **t** | Variables temporelles | Integer[2] | [14,3] (14h, mercredi) |
| **s** | Agents disponibles | Integer | 5 |
| **P_LES** | Prédicteur LES | Float | 120.5 |
| **P_Avg-LES** | Prédicteur Avg-LES | Float | 98.2 |

### Variable Cible (y)
| Variable | Description | Type | Exemple |
|----------|-------------|------|---------|
| **W** | Temps d'attente réel | Float | 135.8 (secondes) |

## Algorithmes de Prédiction

### Prédicteur LES (Linear Exponential Smoothing)
```java
if (queueLength == 0) {
    lesPredictor = avgServiceTime / availableAgents;
} else {
    avgPosition = (queueLength + 1.0) / 2.0;
    lesPredictor = (avgPosition * avgServiceTime) / availableAgents;
}
```

### Prédicteur Avg-LES
```java
avgLesPredictor = getRecentAverageWaitTime(serviceType);
loadFactor = queueLength / availableAgents;
avgLesPredictor *= (1 + loadFactor * 0.1); // Ajustement charge
```

## Gestion des Agents

### Codes d'Activité
- **Disponibles** : [3, 16] (Campagne voeren, WRAP-UP)
- **Indisponibles** : [2, 7, 8, 35, 39, 40, 41, 42, 43, 44, 61, 71]

### Logique de Disponibilité
```java
public boolean isAvailableAt(LocalDateTime time) {
    // Indisponible si occupé avec un appel
    if (busyWithCallUntil != null && time.isBefore(busyWithCallUntil)) {
        return false;
    }
    // Indisponible si dans une activité non disponible
    return unavailableUntil == null || !time.isBefore(unavailableUntil);
}
```

## Filtres de Qualité

### Validation des Échantillons
```java
private boolean isValidSample(SystemState state) {
    return state.getActualWaitTime() >= 0 &&
           state.getActualWaitTime() < 7200 &&  // Max 2h d'attente
           state.getQueueLength() < 500 &&      // Files raisonnables
           state.getAvailableAgents() > 0;      // Au moins 1 agent
}
```

## Configuration et Paramètres

### Sélection des Services
- **Critère** : Volume ≥ 1000 appels
- **Nombre** : Top 8 services (99% du volume)
- **Filtrage** : Appels complets (dateReceived, answered, hangup)

### Fenêtres Glissantes
- **Temps d'attente récents** : 200 derniers appels
- **Temps de service récents** : 200 derniers appels

### Valeurs par Défaut
- **Temps de service moyen** : 180 secondes
- **Temps d'attente moyen** : 60 secondes

## Utilisation

### Exécution Standard
```java
public static void main(String[] args) {
    CallCenterSimulation simulation = new CallCenterSimulation();
    simulation.runHistoricalReplay(
        "data/all_calls_2014_clean.csv", 
        "data/all_activities_2014_clean.csv"
    );
}
```

### Fichiers d'Entrée Requis
1. **all_calls_2014_clean.csv** : Données historiques des appels
2. **all_activities_2014_clean.csv** : Activités des agents

### Fichier de Sortie
- **training_dataset_vanad_ann.csv** : Dataset prêt pour l'ANN

## Métriques de Performance

### Validation des Prédicteurs
```java
// RMSE (Root Mean Square Error)
double rmse = Math.sqrt(mse);

// RRMSE (Relative RMSE)
double rrmse = rmse / avgWaitTime;
```

### Statistiques Générées
- Échantillons total
- Temps d'attente moyen
- Longueur de file moyenne
- Performance des prédicteurs de référence
- Répartition train/test (80/20)

## Avantages de l'Approche

### 1. Fidélité Temporelle
- Reconstitution exacte de l'état à chaque instant
- Prise en compte des vrais états des agents
- Chronologie respectée des événements

### 2. Qualité du Dataset
- Échantillons validés et filtrés
- Structure optimisée pour l'ANN
- Variables temporelles intégrées

### 3. Prédicteurs de Référence
- LES et Avg-LES calculés fidèlement
- Base de comparaison pour l'ANN
- Métriques de performance incluses

## Limitations et Considérations

### 1. Complexité Computationnelle
- Traitement séquentiel obligatoire
- Mémoire proportionnelle au nombre d'agents
- Temps d'exécution linéaire avec les appels

### 2. Dépendances aux Données
- Qualité des données historiques critique
- Compétences agents déduites des appels traités
- Activités agents doivent être complètes

### 3. Hypothèses du Modèle
- Fenêtres glissantes fixes
- Codes d'activité prédéfinis
- Seuils de validation arbitraires

## Extensions Possibles

### 1. Amélioration des Prédicteurs
- Intégration de patterns saisonniers
- Prédicteurs adaptatifs
- Machine learning pour les temps de service

### 2. Optimisation Performance
- Traitement parallèle par période
- Indexation des activités
- Cache des calculs répétitifs

### 3. Validation Croisée
- Splitting temporel intelligent
- Validation sur différentes périodes
- Métriques métier spécifiques

---

## Références Techniques

- **Format dataset** : Conforme spécifications cahier des charges
- **Encodage services** : Mapping consistant 1-8
- **Variables temporelles** : Heure (0-23), Jour semaine (1-7)
- **Précision flottante** : 2 décimales pour les prédicteurs