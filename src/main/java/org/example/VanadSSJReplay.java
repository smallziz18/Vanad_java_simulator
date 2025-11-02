package org.example;

import umontreal.ssj.simevents.*;
import umontreal.ssj.simevents.eventlist.*;
import umontreal.ssj.util.Chrono;
import org.example.model.*;

import java.io.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Replay fidèle VANAD avec SSJ SimEvents et SimView
 * Version professionnelle optimisée pour la génération de dataset
 */
public class VanadSSJReplay extends Simulator {

    // === CONFIGURATION ===
    private static final int MAX_WAIT_TIME = 7200; // 2 heures max
    private static final int RECENT_METRICS_SIZE = 200;
    private static final double TRAINING_SPLIT = 0.8;
    private static final double MIN_EVENT_INTERVAL = 0.001; // 3.6 secondes minimum entre événements
    private final Map<Integer, Double> agentLastActivityTime = new HashMap<>();

    // === DONNÉES ===
    private List<Call> historicalCalls;
    private String[] topServices;
    private LocalDateTime simulationStartTime;

    // === ÉTAT SYSTÈME ===
    private final Map<String, LinkedList<Call>> queues = new HashMap<>();
    private final Map<Integer, AgentState> agents = new HashMap<>();
    private final Map<String, CircularBuffer<Double>> recentWaitTimes = new HashMap<>();
    private final Map<String, CircularBuffer<Double>> recentServiceTimes = new HashMap<>();

    // === COLLECTE DONNÉES ===
    private final List<SystemState> capturedStates = new ArrayList<>();
    private final Map<String, ServiceMetrics> serviceMetrics = new HashMap<>();

    private Chrono chronometer;
    private int totalScheduledEvents = 0; // Compteur manuel des événements

    /**
     * Point d'entrée principal
     */
    public static void main(String[] args) {
        System.out.println("=== VANAD REPLAY AVEC SSJ SIMEVENTS ===");

        try {
            VanadSSJReplay replay = new VanadSSJReplay();
            replay.executeReplay("data/all_calls_2014_clean.csv", "data/all_activities_2014_clean.csv");
        } catch (Exception e) {
            System.err.println("ERREUR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Exécution complète du replay
     */
    public void executeReplay(String callsFile, String activitiesFile) throws Exception {
        chronometer = new Chrono();
        chronometer.init();

        // 1. Chargement et préparation des données
        loadAndPrepareData(callsFile, activitiesFile);

        // 2. Debug des temps pour identifier les problèmes
        debugEventTimes();

        // 3. Initialisation SSJ
        initializeSSJ();

        // 4. Replay avec simulation d'événements
        runReplaySimulation();

        // 5. Export des résultats
        exportResults();

        System.out.printf("Replay terminé en %.2f secondes\n", chronometer.getSeconds());
    }

    /**
     * Chargement et préparation des données historiques
     */
    private void loadAndPrepareData(String callsFile, String activitiesFile) throws Exception {
        System.out.println("Chargement des données historiques...");

        // Charger tous les appels
        List<Call> allCalls = CSVReaderUtil.readCalls(callsFile);
        List<Activity> agentActivities = CSVReaderUtil.readActivities(activitiesFile);

        // Identifier TOP 5 services (volume > 200)
        Map<String, Long> serviceVolumes = allCalls.stream()
                .filter(call -> call.getQueueName() != null && call.getDateReceived() != null)
                .collect(Collectors.groupingBy(Call::getQueueName, Collectors.counting()));

        topServices = serviceVolumes.entrySet().stream()
                .filter(entry -> entry.getValue() >= 200)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toArray(String[]::new);

        // Filtrage plus strict et tri chronologique
        historicalCalls = allCalls.stream()
                .filter(call -> Arrays.asList(topServices).contains(call.getQueueName()))
                .filter(this::isValidCall)
                .sorted(Comparator.comparing(Call::getDateReceived))
                .collect(Collectors.toList());

        // Définir le temps de référence pour la simulation
        if (!historicalCalls.isEmpty()) {
            simulationStartTime = historicalCalls.get(0).getDateReceived();
            LocalDateTime lastCall = historicalCalls.get(historicalCalls.size() - 1).getDateReceived();
            System.out.printf("Période de simulation: %s à %s\n", simulationStartTime, lastCall);
            System.out.printf("Durée totale: %d heures\n",
                    ChronoUnit.HOURS.between(simulationStartTime, lastCall));
        }

        System.out.printf("Services: %s\n", Arrays.toString(topServices));
        System.out.printf("Appels valides: %d\n", historicalCalls.size());
        System.out.printf("Activités agents: %d\n", agentActivities.size());
    }

    /**
     * Méthode de debugging pour vérifier les temps
     */
    private void debugEventTimes() {
        if (historicalCalls.isEmpty()) return;

        System.out.println("=== DEBUG TEMPS ÉVÉNEMENTS ===");

        // Vérifier les 10 premiers appels
        for (int i = 0; i < Math.min(10, historicalCalls.size()); i++) {
            Call call = historicalCalls.get(i);
            double arrivalTime = timeToDouble(call.getDateReceived());

            System.out.printf("Appel %d: arrival=%.2f", i, arrivalTime);

            if (call.getAnswered() != null) {
                double answerTime = timeToDouble(call.getAnswered());
                System.out.printf(", answer=%.2f", answerTime);
            }

            if (call.getHangup() != null) {
                double hangupTime = timeToDouble(call.getHangup());
                System.out.printf(", hangup=%.2f", hangupTime);
            }

            System.out.println();
        }

        // Vérifier qu'il n'y a pas d'événements simultanés problématiques
        Map<Double, Integer> timeFrequency = new HashMap<>();
        for (Call call : historicalCalls.subList(0, Math.min(100, historicalCalls.size()))) {
            double arrivalTime = timeToDouble(call.getDateReceived());
            timeFrequency.merge(arrivalTime, 1, Integer::sum);
        }

        long simultaneousEvents = timeFrequency.values().stream()
                .filter(count -> count > 1)
                .count();

        if (simultaneousEvents > 0) {
            System.out.printf("ATTENTION: %d moments avec événements simultanés détectés\n", simultaneousEvents);
        }
    }

    /**
     * Validation d'un appel
     */
    private boolean isValidCall(Call call) {
        if (call.getDateReceived() == null) return false;

        // Vérifier la cohérence temporelle
        if (call.getAnswered() != null) {
            long waitTime = ChronoUnit.SECONDS.between(call.getDateReceived(), call.getAnswered());
            if (waitTime < 0 || waitTime >= MAX_WAIT_TIME) return false;
        }

        if (call.getHangup() != null) {
            // Si l'appel a été répondu, vérifier que hangup >= answered
            if (call.getAnswered() != null) {
                long serviceTime = ChronoUnit.SECONDS.between(call.getAnswered(), call.getHangup());
                return serviceTime >= 0;
            } else {
                // Si l'appel n'a pas été répondu, vérifier que hangup >= received
                long totalTime = ChronoUnit.SECONDS.between(call.getDateReceived(), call.getHangup());
                return totalTime >= 0;
            }
        }

        return true;
    }

    /**
     * Initialisation des composants SSJ
     */
    private void initializeSSJ() {
        System.out.println("Initialisation SSJ SimEvents...");

        // Configuration EventList AVANT l'initialisation
        EventList eventList = new DoublyLinked();
        Sim.init(eventList);

        // Initialisation des files d'attente
        for (String service : topServices) {
            queues.put(service, new LinkedList<>());
            recentWaitTimes.put(service, new CircularBuffer<>(RECENT_METRICS_SIZE));
            recentServiceTimes.put(service, new CircularBuffer<>(RECENT_METRICS_SIZE));
            serviceMetrics.put(service, new ServiceMetrics(service));
        }

        // Initialisation des agents avec compétences
        initializeAgents();

        // Tri des appels par date avant programmation
        historicalCalls.sort(Comparator.comparing(Call::getDateReceived));

        // Programmation des événements
        scheduleAllEvents();

        System.out.printf("Agents configurés: %d\n", agents.size());
        System.out.printf("Événements programmés: %d\n", totalScheduledEvents);
    }

    /**
     * Initialisation des agents avec leurs compétences
     */
    private void initializeAgents() {
        Map<Integer, Set<String>> agentSkills = historicalCalls.stream()
                .filter(call -> call.getAgentNumber() != null)
                .collect(Collectors.groupingBy(
                        call -> call.getAgentNumber().intValue(),
                        Collectors.mapping(Call::getQueueName, Collectors.toSet())
                ));

        agentSkills.forEach((agentId, skills) -> {
            AgentState agent = new AgentState(agentId, skills);
            agents.put(agentId, agent);
            agentLastActivityTime.put(agentId, 0.0); // Initialiser à 0
        });

        // Statistiques par service
        for (String service : topServices) {
            long competentAgents = agents.values().stream()
                    .filter(agent -> agent.canHandle(service))
                    .count();
            System.out.printf("Service %s: %d agents compétents\n", service, competentAgents);
        }
    }

    /**
     * Programmation de tous les événements dans SSJ
     */
    private void scheduleAllEvents() {
        System.out.println("Programmation des événements...");

        List<ScheduledEvent> allEvents = new ArrayList<>();


        // collect et validation des évènements
        for (Call call : historicalCalls) {
            try {
                double arrivalTime = timeToDouble(call.getDateReceived());

                if (arrivalTime < 0) {
                    System.err.printf("Invalid arrival time for call: %f\n", arrivalTime);
                    continue;
                }

                allEvents.add(new ScheduledEvent(arrivalTime, new CallArrivalEvent(call)));

                if (call.getAnswered() != null) {
                    double answerTime = timeToDouble(call.getAnswered());
                    if (answerTime >= arrivalTime) {
                        allEvents.add(new ScheduledEvent(answerTime, new CallAnsweredEvent(call)));
                    }
                }

                if (call.getHangup() != null) {
                    double hangupTime = timeToDouble(call.getHangup());
                    if (hangupTime >= arrivalTime) {
                        allEvents.add(new ScheduledEvent(hangupTime, new CallHangupEvent(call)));
                    }
                }

            } catch (Exception e) {
                System.err.printf("Error processing call: %s\n", e.getMessage());
            }
        }

        //
        allEvents.sort(Comparator.comparing(ScheduledEvent::time));

        double lastTime = 0.0;
        for (ScheduledEvent event : allEvents) {
            double eventTime = Math.max(event.time(), lastTime + MIN_EVENT_INTERVAL);

            try {
                event.event().schedule(eventTime);
                lastTime = eventTime;
                totalScheduledEvents++;
            } catch (Exception e) {
                System.err.printf("Failed to schedule event at time %f: %s\n",
                        eventTime, e.getMessage());
            }
        }

        System.out.printf("Successfully scheduled %d events\n", totalScheduledEvents);
    }


    /**
     * Conversion LocalDateTime vers double (heures relatives)
     */
    private double timeToDouble(LocalDateTime time) {
        if (simulationStartTime == null) {
            throw new IllegalStateException("simulationStartTime n'est pas initialisé");
        }
        return ChronoUnit.SECONDS.between(simulationStartTime, time) / 3600.0;
    }

    /**
     * Exécution de la simulation de replay
     */
    private void runReplaySimulation() {
        System.out.println("Démarrage du replay avec simulation d'événements...");
        chronometer.init();

        Sim.start();

        System.out.printf("Simulation terminée. États capturés: %d\n", capturedStates.size());
    }

    /**
     * Export des résultats
     */
    private void exportResults() throws IOException {
        System.out.println("Export des datasets...");

        if (capturedStates.isEmpty()) {
            throw new IllegalStateException("Aucun état capturé!");
        }

        // Mélange pour éviter biais temporels
        Collections.shuffle(capturedStates, new Random(42));

        // Division train/test
        int trainSize = (int) (capturedStates.size() * TRAINING_SPLIT);
        List<SystemState> trainData = capturedStates.subList(0, trainSize);
        List<SystemState> testData = capturedStates.subList(trainSize, capturedStates.size());

        // Export
        exportDataset(trainData, "vanad_training_ssj.csv");
        exportDataset(testData, "vanad_test_ssj.csv");

        // Statistiques
        printStatistics();

        System.out.printf("Datasets exportés: %d entraînement, %d test\n",
                trainData.size(), testData.size());
    }

    /**
     * Export d'un dataset au format CSV
     */
    private void exportDataset(List<SystemState> data, String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // En-tête selon les spécifications (ANN sera calculé en Python)
            writer.println("T,qT,l1,l2,l3,l4,t_hour,t_day_of_week,s,P_LES,P_Avg_LES,W");

            for (SystemState state : data) {
                writer.printf(Locale.US, "%d,%d,%d,%d,%d,%d,%d,%d,%d,%.2f,%.2f,%.2f%n",
                        encodeService(state.getServiceType()),
                        state.getQueueLength(),
                        getOtherQueueLength(state, 0),
                        getOtherQueueLength(state, 1),
                        getOtherQueueLength(state, 2),
                        getOtherQueueLength(state, 3),
                        state.getArrivalTime().getHour(),
                        state.getArrivalTime().getDayOfWeek().getValue(),
                        state.getAvailableAgents(),
                        state.getLesPredictor(),
                        state.getAvgLesPredictor(),
                        state.getActualWaitTime()
                );
            }
        }
    }

    private int encodeService(String service) {
        return Arrays.asList(topServices).indexOf(service) + 1;
    }

    private int getOtherQueueLength(SystemState state, int index) {
        int[] others = state.getOtherQueuesLengths();
        return index < others.length ? others[index] : 0;
    }

    /**
     * Affichage des statistiques
     */
    private void printStatistics() {
        System.out.println("\n=== STATISTIQUES REPLAY SSJ ===");

        double avgWait = capturedStates.stream()
                .mapToDouble(SystemState::getActualWaitTime)
                .average().orElse(0);

        double avgQueue = capturedStates.stream()
                .mapToInt(SystemState::getQueueLength)
                .average().orElse(0);

        System.out.printf("États capturés: %d\n", capturedStates.size());
        System.out.printf("Temps d'attente moyen: %.2f sec (%.2f min)\n", avgWait, avgWait/60);
        System.out.printf("Longueur file moyenne: %.2f\n", avgQueue);

        // Par service
        for (String service : topServices) {
            long count = capturedStates.stream()
                    .filter(s -> s.getServiceType().equals(service))
                    .count();

            double serviceAvgWait = capturedStates.stream()
                    .filter(s -> s.getServiceType().equals(service))
                    .mapToDouble(SystemState::getActualWaitTime)
                    .average().orElse(0);

            System.out.printf("Service %s: %d échantillons, %.2f sec attente moyenne\n",
                    service, count, serviceAvgWait);
        }
    }

    // ========================
    // CLASSES UTILITAIRES
    // ========================

    /**
         * Classe pour gérer les événements programmés
         */
        private record ScheduledEvent(double time, Event event) {
    }

    // ========================
    // ÉVÉNEMENTS SSJ
    // ========================

    /**
     * Événement d'arrivée d'appel
     */
    class CallArrivalEvent extends Event {
        private final Call call;

        public CallArrivalEvent(Call call) {
            this.call = call;
        }

        @Override
        public void actions() {
            String serviceType = call.getQueueName();

            // CAPTURE DE L'ÉTAT AVANT MODIFICATION
            SystemState state = captureSystemState(call);

            // ROUTAGE SELON SPÉCIFICATIONS VANAD
            boolean routed = routeCall(call);

            if (!routed) {
                // Pas d'agent disponible -> file d'attente (FCFS)
                queues.get(serviceType).offer(call);
            }
            // Si routé, l'appel n'entre pas en file d'attente

            // Sauvegarde si valide
            if (isValidState(state)) {
                capturedStates.add(state);
            }

            // Mise à jour métriques
            serviceMetrics.get(serviceType).recordArrival();
        }
    }

    /**
     * Événement de réponse à un appel
     */
    //  CallAnsweredEvent pour gérer le routage
    class CallAnsweredEvent extends Event {
        private final Call call;
        private final Integer assignedAgentId;

        public CallAnsweredEvent(Call call) {
            this.call = call;
            this.assignedAgentId = null;
        }

        public CallAnsweredEvent(Call call, Integer assignedAgentId) {
            this.call = call;
            this.assignedAgentId = assignedAgentId;
        }

        @Override
        public void actions() {
            String serviceType = call.getQueueName();

            //  remove by call ID if available
            LinkedList<Call> queue = queues.get(serviceType);
            boolean removed = false;

            // Try to remove exact call first
            Iterator<Call> iterator = queue.iterator();
            while (iterator.hasNext()) {
                Call queuedCall = iterator.next();
                if (isSameCall(queuedCall, call)) {
                    iterator.remove();
                    removed = true;
                    break;
                }
            }


            if (!removed) {
                System.out.println("Call was routed directly, not queued");
            }

            // Utiliser l'agent assigné ou celui du fichier de données
            Integer agentId = assignedAgentId != null ? assignedAgentId :
                    (call.getAgentNumber() != null ? call.getAgentNumber().intValue() : null);

            if (agentId != null) {
                AgentState agent = agents.get(agentId);
                if (agent != null) {
                    agent.setBusy(true);
                    agent.setLastActivityTime(Sim.time());
                }
            }

            serviceMetrics.get(serviceType).recordAnswer();

        }
    }

    private boolean isSameCall(Call c1, Call c2) {
        return c1.getDateReceived().equals(c2.getDateReceived()) &&
                c1.getQueueName().equals(c2.getQueueName()) &&
                Objects.equals(c1.getAgentNumber(), c2.getAgentNumber());
    }

    // 8. Modifier CallHangupEvent pour déclencher le routage
    class CallHangupEvent extends Event {
        private final Call call;

        public CallHangupEvent(Call call) {
            this.call = call;
        }

        @Override
        public void actions() {
            String serviceType = call.getQueueName();

            // Libération agent
            if (call.getAgentNumber() != null) {
                AgentState agent = agents.get(call.getAgentNumber().intValue());
                if (agent != null) {
                    agent.setBusy(false);
                    agent.setLastActivityTime(Sim.time());
                }
            }

            // Enregistrement métriques
            recordCallMetrics(call);
            serviceMetrics.get(serviceType).recordHangup();

            // ROUTAGE DU PROCHAIN APPEL EN FILE
            routeNextCallInQueue(serviceType);
        }
    }
    // 9. Méthode pour router le prochain appel en file
    private void routeNextCallInQueue(String serviceType) {
        LinkedList<Call> queue = queues.get(serviceType);

        if (!queue.isEmpty()) {
            AgentState availableAgent = findLongestIdleAgent(serviceType);

            if (availableAgent != null) {
                Call nextCall = queue.poll(); // FCFS

                // Router l'appel
                availableAgent.setBusy(true);
                availableAgent.setLastActivityTime(Sim.time());

                // Programmer la réponse immédiate ou selon les données historiques
                double answerDelay = 0.1; // Délai minimal pour répondre
                new CallAnsweredEvent(nextCall, availableAgent.getAgentId())
                        .schedule(Sim.time() + answerDelay);
            }
        }
    }






    // ========================
    // CAPTURE D'ÉTAT
    // ========================

    /**
     * Capture l'état du système pour un appel
     */
    private SystemState captureSystemState(Call call) {
        String serviceType = call.getQueueName();
        LocalDateTime arrivalTime = call.getDateReceived();

        if (arrivalTime == null) {
            throw new IllegalArgumentException("Call arrival time is null");
        }

        int queueLength = queues.get(serviceType).size();

        // Autres files
        int[] otherQueues = new int[4];
        int idx = 0;
        for (String service : topServices) {
            if (!service.equals(serviceType) && idx < 4) {
                otherQueues[idx] = queues.get(service).size();
                idx++;
            }
        }

        // Agents disponibles
        int availableAgents = (int) agents.values().stream()
                .filter(agent -> agent.canHandle(serviceType))
                .filter(AgentState::isAvailable)
                .count();

        // Création état
        SystemState state = new SystemState(serviceType, queueLength, otherQueues,
                arrivalTime, Math.max(1, availableAgents));

        // CALCUL DES PRÉDICTEURS (correction principale)
        calculatePredictors(state);

        // Temps d'attente réel
        if (call.getAnswered() != null) {
            double actualWaitTime = ChronoUnit.SECONDS.between(
                    call.getDateReceived(), call.getAnswered()
            );
            state.setActualWaitTime(Math.max(0, actualWaitTime));
        } else {
            // Pour les appels abandonnés, estimer le temps d'attente
            if (call.getHangup() != null) {
                double abandonTime = ChronoUnit.SECONDS.between(
                        call.getDateReceived(), call.getHangup()
                );
                state.setActualWaitTime(Math.max(0, abandonTime));
            }
        }

        return state;
    }

    /**
     * Calcul des prédicteurs LES et Avg-LES
     */
    private void calculatePredictors(SystemState state) {
        String serviceType = state.getServiceType();

        // 1. Calcul du prédicteur LES (Last Experience Sharing)
        // LES = temps d'attente moyen des N derniers clients du même service
        CircularBuffer<Double> recentWaits = recentWaitTimes.get(serviceType);
        double lesPredictor = 0.0;

        if (!recentWaits.isEmpty()) {
            lesPredictor = recentWaits.getAverage();
        } else {
            // Valeur par défaut si pas d'historique
            lesPredictor = estimateDefaultWaitTime(serviceType, state.getQueueLength(), state.getAvailableAgents());
        }

        // 2. Calcul du prédicteur Avg-LES (Average LES across all services)
        // Avg-LES = moyenne pondérée des LES de tous les services
        double avgLesPredictor = 0.0;
        double totalWeight = 0.0;

        for (String service : topServices) {
            CircularBuffer<Double> serviceWaits = recentWaitTimes.get(service);
            if (!serviceWaits.isEmpty()) {
                double weight = Math.max(1.0, serviceWaits.size()); // Poids basé sur le nombre d'échantillons
                avgLesPredictor += serviceWaits.getAverage() * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight > 0) {
            avgLesPredictor /= totalWeight;
        } else {
            avgLesPredictor = lesPredictor; // Fallback sur LES si pas d'historique
        }

        // 3. Ajustement basé sur l'état actuel du système
        // Facteur de correction basé sur la charge actuelle
        double queueFactor = Math.max(0.1, state.getQueueLength() / Math.max(1.0, state.getAvailableAgents()));
        double avgServiceTime = getAverageServiceTime(serviceType);

        // Ajustement LES
        lesPredictor = Math.max(0, lesPredictor + (queueFactor * avgServiceTime * 0.1));

        // Ajustement Avg-LES
        avgLesPredictor = Math.max(0, avgLesPredictor + (queueFactor * avgServiceTime * 0.1));

        // 4. Application des prédicteurs à l'état
        state.setLesPredictor(lesPredictor);
        state.setAvgLesPredictor(avgLesPredictor);
    }

    /**
     * Estimation du temps d'attente par défaut quand pas d'historique
     */
    private double estimateDefaultWaitTime(String serviceType, int queueLength, int availableAgents) {
        double avgServiceTime = getAverageServiceTime(serviceType);

        if (availableAgents > 0) {
            // Estimation simple : (nombre en file / agents disponibles) * temps service moyen
            return (queueLength / (double) availableAgents) * avgServiceTime;
        } else {
            // Si pas d'agents disponibles, temps d'attente élevé
            return queueLength * avgServiceTime * 2.0;
        }
    }

    /**
     * Obtient le temps de service moyen
     */
    private double getAverageServiceTime(String serviceType) {
        if (recentServiceTimes.get(serviceType).isEmpty()) {
            return switch (serviceType.toLowerCase()) {
                case "technical", "support" -> 300.0;
                case "sales", "billing" -> 180.0;
                default -> 240.0;
            };
        }
        return recentServiceTimes.get(serviceType).getAverage();
    }

    /**
     * Enregistre les métriques d'un appel
     */
    private void recordCallMetrics(Call call) {
        String serviceType = call.getQueueName();

        // Temps d'attente
        if (call.getDateReceived() != null && call.getAnswered() != null) {
            double waitTime = ChronoUnit.SECONDS.between(
                    call.getDateReceived(), call.getAnswered()
            );
            if (waitTime >= 0 && waitTime < MAX_WAIT_TIME) {
                recentWaitTimes.get(serviceType).add(waitTime);
            }
        }

        // Temps de service
        if (call.getAnswered() != null && call.getHangup() != null) {
            double serviceTime = ChronoUnit.SECONDS.between(
                    call.getAnswered(), call.getHangup()
            );
            if (serviceTime > 0 && serviceTime < 3600) {
                recentServiceTimes.get(serviceType).add(serviceTime);
            }
        }
    }

    /**
     * Validation d'un état système
     */
    private boolean isValidState(SystemState state) {
        return state.getActualWaitTime() >= 0 &&
                state.getActualWaitTime() < MAX_WAIT_TIME &&
                state.getQueueLength() >= 0 &&
                state.getAvailableAgents() > 0;
    }

    // ========================
    // CLASSES UTILITAIRES SUPPLÉMENTAIRES
    // ========================

    /**
     * Buffer circulaire pour métriques récentes
     */
    static class CircularBuffer<T extends Number> {
        private final List<T> buffer;
        private final int maxSize;
        private int index = 0;

        public CircularBuffer(int maxSize) {
            this.maxSize = maxSize;
            this.buffer = new ArrayList<>(maxSize);
        }

        public void add(T value) {
            if (buffer.size() < maxSize) {
                buffer.add(value);
            } else {
                buffer.set(index, value);
                index = (index + 1) % maxSize;
            }
        }

        public boolean isEmpty() {
            return buffer.isEmpty();
        }

        public double getAverage() {
            return buffer.isEmpty() ? 0.0 :
                    buffer.stream().mapToDouble(Number::doubleValue).average().orElse(0.0);
        }
        public int size() {
            return buffer.size();
        }


    }

    /**
     * État d'un agent
     */
    static class AgentState {
        private final int agentId;
        private final Set<String> skills;
        private boolean busy = false;
        private double lastActivityTime = 0.0;

        public AgentState(int agentId, Set<String> skills) {
            this.agentId = agentId;
            this.skills = new HashSet<>(skills);
        }

        public boolean canHandle(String serviceType) {
            return skills.contains(serviceType);
        }

        // CORRECTION : Logique corrigée
        public boolean isAvailable() {
            return !busy; // Agent libre si pas occupé
        }


        public void setBusy(boolean busy) {
            this.busy = busy;
        }

        public int getAgentId() { return agentId; }
        public double getLastActivityTime() { return lastActivityTime; }
        public void setLastActivityTime(double time) { this.lastActivityTime = time; }
    }

    private AgentState findLongestIdleAgent(String serviceType) {
        return agents.values().stream()
                .filter(agent -> agent.canHandle(serviceType))
                .filter(AgentState::isAvailable)  // Agents libres
                .min(Comparator.comparing(AgentState::getLastActivityTime))
                .orElse(null);
    }
    // 4. Méthode de routage principal
    private boolean routeCall(Call call) {
        String serviceType = call.getQueueName();
        AgentState selectedAgent = findLongestIdleAgent(serviceType);

        if (selectedAgent != null) {
            // Route immediately
            selectedAgent.setBusy(true);
            selectedAgent.setLastActivityTime(Sim.time());

            // Log successful routing
            System.out.printf("Routed call to agent %d at time %.2f\n",
                    selectedAgent.getAgentId(), Sim.time());

            return true;
        }

        // Log queue entry
        System.out.printf("Call queued for service %s at time %.2f\n",
                serviceType, Sim.time());
        return false;
    }

    /**
     * Métriques par service
     */
    static class ServiceMetrics {
        int arrivals = 0;
         int answers = 0;
         int hangups = 0;

        public ServiceMetrics(String serviceName) {
        }

        public void recordArrival() { arrivals++; }
        public void recordAnswer() { answers++; }
        public void recordHangup() { hangups++; }


    }
}