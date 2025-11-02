// ===== Activity.java =====
package org.example.model;

import java.time.LocalDateTime;

/**
 * Classe représentant une activité d'agent dans le système VANAD
 * Format CSV: id,user_id,dnd_id,campaign_id,extension,last_call_id,startdatetime,enddatetime,agent_id,year,month,day,day_of_week,hour,minute,secondes,time_of_day,duration
 */
public class Activity {

    // Champs principaux
    private Long id;
    private Integer userId;
    private Integer dndId;
    private Integer campaignId;
    private Integer extension;
    private Integer lastCallId;

    // Champs de timing
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private Integer agentId;

    // Champs calculés de date/heure
    private Integer year;
    private Integer month;
    private Integer day;
    private Integer dayOfWeek;
    private Integer hour;
    private Integer minute;
    private Integer seconds;
    private Double timeOfDay;
    private Double duration;

    // Alias pour compatibilité avec le code de simulation
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public Integer agentID;

    // Constructeurs
    public Activity() {}

    public Activity(Long id, Integer agentId, LocalDateTime startDateTime) {
        this.id = id;
        this.agentId = agentId;
        this.agentID = agentId; // Alias
        this.startDateTime = startDateTime;
        this.startTime = startDateTime; // Alias
    }

    // Getters et Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getDndId() {
        return dndId;
    }

    public void setDndId(Integer dndId) {
        this.dndId = dndId;
    }

    public Integer getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Integer campaignId) {
        this.campaignId = campaignId;
    }

    public Integer getExtension() {
        return extension;
    }

    public void setExtension(Integer extension) {
        this.extension = extension;
    }

    public Integer getLastCallId() {
        return lastCallId;
    }

    public void setLastCallId(Integer lastCallId) {
        this.lastCallId = lastCallId;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
        this.startTime = startDateTime; // Maintenir l'alias
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(LocalDateTime endDateTime) {
        this.endDateTime = endDateTime;
        this.endTime = endDateTime; // Maintenir l'alias
    }

    public Integer getAgentId() {
        return agentId;
    }

    public void setAgentId(Integer agentId) {
        this.agentId = agentId;
        this.agentID = agentId; // Maintenir l'alias
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

    public Integer getSeconds() {
        return seconds;
    }

    public void setSeconds(Integer seconds) {
        this.seconds = seconds;
    }

    public Double getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeOfDay(Double timeOfDay) {
        this.timeOfDay = timeOfDay;
    }

    public Double getDuration() {
        return duration;
    }

    public void setDuration(Double duration) {
        this.duration = duration;
    }

    @Override
    public String toString() {
        return "Activity{" +
                "id=" + id +
                ", agentId=" + agentId +
                ", startDateTime=" + startDateTime +
                ", endDateTime=" + endDateTime +
                ", duration=" + duration +
                '}';
    }

    public Integer getActivityId() {
        return Math.toIntExact(this.id);
    }
}
