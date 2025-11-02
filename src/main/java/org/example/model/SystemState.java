package org.example.model;

import java.time.LocalDateTime;

/**
 * Classe représentant l'état du système à un moment donné
 * Utilisée pour générer le dataset d'entraînement pour l'ANN
 */
public class SystemState {

    // Caractéristiques du système
    private String serviceType;
    private int queueLength;
    private int[] otherQueuesLengths;
    private LocalDateTime arrivalTime;
    private int availableAgents;

    // Prédicteurs
    private double lesPredictor;      // Linear Exponential Smoothing
    private double avgLesPredictor;   // Average Linear Exponential Smoothing

    // Variable cible
    private double actualWaitTime;    // Temps d'attente réel (en secondes)

    // Constructeurs
    public SystemState() {}

    public SystemState(String serviceType, int queueLength, int[] otherQueuesLengths,
                       LocalDateTime arrivalTime, int availableAgents) {
        this.serviceType = serviceType;
        this.queueLength = queueLength;
        this.otherQueuesLengths = otherQueuesLengths;
        this.arrivalTime = arrivalTime;
        this.availableAgents = availableAgents;
    }

    /**
     * Convertit l'état du système en vecteur de caractéristiques pour l'ANN
     */
    public double[] toFeatureVector() {
        // Encoder le type de service (one-hot encoding simplifié)
        double serviceType30175 = "30175".equals(serviceType) ? 1.0 : 0.0;
        double serviceType30560 = "30560".equals(serviceType) ? 1.0 : 0.0;
        double serviceType30172 = "30172".equals(serviceType) ? 1.0 : 0.0;

        // Caractéristiques temporelles
        double hour = arrivalTime != null ? arrivalTime.getHour() : 0.0;
        double dayOfWeek = arrivalTime != null ? arrivalTime.getDayOfWeek().getValue() : 0.0;

        // Longueurs des autres files (padding si nécessaire)
        double otherQueue1 = otherQueuesLengths.length > 0 ? otherQueuesLengths[0] : 0.0;
        double otherQueue2 = otherQueuesLengths.length > 1 ? otherQueuesLengths[1] : 0.0;

        return new double[] {
                serviceType30175,
                serviceType30560,
                serviceType30172,
                queueLength,
                otherQueue1,
                otherQueue2,
                hour,
                dayOfWeek,
                availableAgents,
                lesPredictor,
                avgLesPredictor
        };
    }

    /**
     * Retourne les noms des caractéristiques pour l'export CSV
     */
    public static String[] getFeatureNames() {
        return new String[] {
                "service_type_30175",
                "service_type_30560",
                "service_type_30172",
                "queue_length",
                "other_queue_1",
                "other_queue_2",
                "arrival_hour",
                "day_of_week",
                "available_agents",
                "les_predictor",
                "avg_les_predictor"
        };
    }

    // Getters et Setters
    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public int getQueueLength() {
        return queueLength;
    }

    public void setQueueLength(int queueLength) {
        this.queueLength = queueLength;
    }

    public int[] getOtherQueuesLengths() {
        return otherQueuesLengths;
    }

    public void setOtherQueuesLengths(int[] otherQueuesLengths) {
        this.otherQueuesLengths = otherQueuesLengths;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalDateTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public int getAvailableAgents() {
        return availableAgents;
    }

    public void setAvailableAgents(int availableAgents) {
        this.availableAgents = availableAgents;
    }

    public double getLesPredictor() {
        return lesPredictor;
    }

    public void setLesPredictor(double lesPredictor) {
        this.lesPredictor = lesPredictor;
    }

    public double getAvgLesPredictor() {
        return avgLesPredictor;
    }

    public void setAvgLesPredictor(double avgLesPredictor) {
        this.avgLesPredictor = avgLesPredictor;
    }

    public double getActualWaitTime() {
        return actualWaitTime;
    }

    public void setActualWaitTime(double actualWaitTime) {
        this.actualWaitTime = actualWaitTime;
    }

    @Override
    public String toString() {
        return "SystemState{" +
                "serviceType='" + serviceType + '\'' +
                ", queueLength=" + queueLength +
                ", availableAgents=" + availableAgents +
                ", lesPredictor=" + lesPredictor +
                ", avgLesPredictor=" + avgLesPredictor +
                ", actualWaitTime=" + actualWaitTime +
                '}';
    }
}