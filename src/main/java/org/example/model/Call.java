
// ===== Call.java =====
package org.example.model;

import java.time.LocalDateTime;

/**
 * Classe représentant un appel dans le système VANAD
 * Format CSV: date_received,queue_name,agent_number,answered,consult,transfer,hangup,year,month,day,day_of_week,hour,minute,time_of_day
 */
public class Call {

    // Champs principaux
    private LocalDateTime dateReceived;
    private String queueName;
    private Double agentNumber;

    // Champs de timing (peuvent être null)
    private LocalDateTime answered;
    private LocalDateTime consult;
    private LocalDateTime transfer;
    private LocalDateTime hangup;

    // Champs calculés de date/heure
    private Integer year;
    private Integer month;
    private Integer day;
    private Integer dayOfWeek;
    private Integer hour;
    private Integer minute;
    private Double timeOfDay;

    // Constructeurs
    public Call() {}

    public Call(LocalDateTime dateReceived, String queueName, Double agentNumber) {
        this.dateReceived = dateReceived;
        this.queueName = queueName;
        this.agentNumber = agentNumber;
    }

    // Getters et Setters
    public LocalDateTime getDateReceived() {
        return dateReceived;
    }

    public void setDateReceived(LocalDateTime dateReceived) {
        this.dateReceived = dateReceived;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public Double getAgentNumber() {
        return agentNumber;
    }

    public void setAgentNumber(Double agentNumber) {
        this.agentNumber = agentNumber;
    }

    public LocalDateTime getAnswered() {
        return answered;
    }

    public void setAnswered(LocalDateTime answered) {
        this.answered = answered;
    }

    public LocalDateTime getConsult() {
        return consult;
    }

    public void setConsult(LocalDateTime consult) {
        this.consult = consult;
    }

    public LocalDateTime getTransfer() {
        return transfer;
    }

    public void setTransfer(LocalDateTime transfer) {
        this.transfer = transfer;
    }

    public LocalDateTime getHangup() {
        return hangup;
    }

    public void setHangup(LocalDateTime hangup) {
        this.hangup = hangup;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public Integer getDay() {
        return day;
    }

    public void setDay(Integer day) {
        this.day = day;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public Integer getHour() {
        return hour;
    }

    public void setHour(Integer hour) {
        this.hour = hour;
    }

    public Integer getMinute() {
        return minute;
    }

    public void setMinute(Integer minute) {
        this.minute = minute;
    }

    public Double getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeOfDay(Double timeOfDay) {
        this.timeOfDay = timeOfDay;
    }

    @Override
    public String toString() {
        return "Call{" +
                "dateReceived=" + dateReceived +
                ", queueName='" + queueName + '\'' +
                ", agentNumber=" + agentNumber +
                ", answered=" + answered +
                ", hangup=" + hangup +
                '}';
    }
}