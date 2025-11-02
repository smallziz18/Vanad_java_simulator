package org.example;

import com.opencsv.CSVReader;
import org.example.model.Activity;
import org.example.model.Call;

import java.io.FileReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Classe utilitaire mise à jour pour lire les fichiers CSV des appels et des activités agents,
 * compatible avec le nouveau format de données VANAD.
 */
public class CSVReaderUtil {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Lit un fichier CSV contenant les appels et retourne une liste d'objets Call.
     * Format CSV: date_received,queue_name,agent_number,answered,consult,transfer,hangup,year,month,day,day_of_week,hour,minute,time_of_day
     */
    public static List<Call> readCalls(String filePath) throws Exception {
        List<Call> calls = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] nextLine;
            int lineNum = 1;
            reader.readNext(); // Ignore l'en-tête

            while ((nextLine = reader.readNext()) != null) {
                lineNum++;
                try {
                    Call call = new Call();

                    // Champs principaux
                    call.setDateReceived(parseDateSafely(nextLine, 0, "date_received", lineNum, false));
                    call.setQueueName(parseStringSafely(nextLine));
                    call.setAgentNumber(parseDoubleSafely(nextLine, 2, "agent_number", lineNum));

                    // Champs de timing (peuvent être null)
                    call.setAnswered(parseDateSafely(nextLine, 3, "answered", lineNum, true));
                    call.setConsult(parseDateSafely(nextLine, 4, "consult", lineNum, true));
                    call.setTransfer(parseDateSafely(nextLine, 5, "transfer", lineNum, true));
                    call.setHangup(parseDateSafely(nextLine, 6, "hangup", lineNum, true));

                    // Champs calculés de date/heure
                    call.setYear(parseIntSafely(nextLine, 7, "year", lineNum));
                    call.setMonth(parseIntSafely(nextLine, 8, "month", lineNum));
                    call.setDay(parseIntSafely(nextLine, 9, "day", lineNum));
                    call.setDayOfWeek(parseIntSafely(nextLine, 10, "day_of_week", lineNum));
                    call.setHour(parseIntSafely(nextLine, 11, "hour", lineNum));
                    call.setMinute(parseIntSafely(nextLine, 12, "minute", lineNum));
                    call.setTimeOfDay(parseDoubleSafely(nextLine, 13, "time_of_day", lineNum));

                    calls.add(call);
                } catch (Exception e) {
                    System.err.printf("[CALL] Échec du parsing à la ligne %d: %s%n", lineNum, Arrays.toString(nextLine));
                    e.printStackTrace();
                }
            }
        }
        System.out.println("[CALL] Nombre total d'appels parsés : " + calls.size());
        return calls;
    }

    /**
     * Lit un fichier CSV contenant les activités agents et retourne une liste d'objets Activity.
     * Format CSV: id,user_id,dnd_id,campaign_id,extension,last_call_id,startdatetime,enddatetime,agent_id,year,month,day,day_of_week,hour,minute,secondes,time_of_day,duration
     */
    public static List<Activity> readActivities(String filePath) throws Exception {
        List<Activity> activities = new ArrayList<>();
        int lineNum = 1;
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] nextLine;
            reader.readNext(); // Ignore l'en-tête

            while ((nextLine = reader.readNext()) != null) {
                lineNum++;
                try {
                    Activity activity = new Activity();

                    // Champs principaux
                    activity.setId(parseLongSafely(nextLine, 0, "id", lineNum));
                    activity.setUserId(parseIntSafely(nextLine, 1, "user_id", lineNum));
                    activity.setDndId(parseIntSafely(nextLine, 2, "dnd_id", lineNum));
                    activity.setCampaignId(parseIntSafely(nextLine, 3, "campaign_id", lineNum));
                    activity.setExtension(parseIntSafely(nextLine, 4, "extension", lineNum));
                    activity.setLastCallId(parseIntSafely(nextLine, 5, "last_call_id", lineNum));

                    // Champs de timing
                    activity.setStartDateTime(parseDateSafely(nextLine, 6, "startdatetime", lineNum, false));
                    activity.setEndDateTime(parseDateSafely(nextLine, 7, "enddatetime", lineNum, true));
                    activity.setAgentId(parseIntSafely(nextLine, 8, "agent_id", lineNum));

                    // Champs calculés de date/heure
                    activity.setYear(parseIntSafely(nextLine, 9, "year", lineNum));
                    activity.setMonth(parseIntSafely(nextLine, 10, "month", lineNum));
                    activity.setDay(parseIntSafely(nextLine, 11, "day", lineNum));
                    activity.setDayOfWeek(parseIntSafely(nextLine, 12, "day_of_week", lineNum));
                    activity.setHour(parseIntSafely(nextLine, 13, "hour", lineNum));
                    activity.setMinute(parseIntSafely(nextLine, 14, "minute", lineNum));
                    activity.setSeconds(parseIntSafely(nextLine, 15, "secondes", lineNum));
                    activity.setTimeOfDay(parseDoubleSafely(nextLine, 16, "time_of_day", lineNum));
                    activity.setDuration(parseDoubleSafely(nextLine, 17, "duration", lineNum));

                    activities.add(activity);
                } catch (Exception e) {
                    System.err.printf("[ACTIVITY] Échec du parsing à la ligne %d: %s%n", lineNum, Arrays.toString(nextLine));
                    e.printStackTrace();
                }
            }
        }
        System.out.println("[ACTIVITY] Nombre total d'activités parsées : " + activities.size());
        return activities;
    }

    // === Méthodes d'aide au parsing ===

    private static LocalDateTime parseDateSafely(String[] fields, int index, String fieldName, int lineNum, boolean allowNull) {
        try {
            if (isEmptyField(fields, index)) {
                if (allowNull) return null;
                throw new IllegalArgumentException("Champ de date requis manquant");
            }
            return LocalDateTime.parse(fields[index].trim(), formatter);
        } catch (Exception e) {
            if (allowNull) return null;
            System.err.printf("[ERROR] Erreur de parsing de date pour '%s' ligne %d: '%s'%n",
                    fieldName, lineNum, getSafe(fields, index));
            throw e;
        }
    }

    private static String parseStringSafely(String[] fields) {
        if (isEmptyField(fields, 1)) {
            return null;
        }
        return fields[1].trim();
    }

    private static Integer parseIntSafely(String[] fields, int index, String fieldName, int lineNum) {
        try {
            if (isEmptyField(fields, index)) return null;
            return Integer.parseInt(fields[index].trim());
        } catch (NumberFormatException e) {
            System.err.printf("[WARN] Erreur de parsing entier pour '%s' ligne %d: '%s'%n",
                    fieldName, lineNum, getSafe(fields, index));
            return null;
        }
    }

    private static Long parseLongSafely(String[] fields, int index, String fieldName, int lineNum) {
        try {
            if (isEmptyField(fields, index)) return null;
            return Long.parseLong(fields[index].trim());
        } catch (NumberFormatException e) {
            System.err.printf("[WARN] Erreur de parsing long pour '%s' ligne %d: '%s'%n",
                    fieldName, lineNum, getSafe(fields, index));
            return null;
        }
    }

    private static Double parseDoubleSafely(String[] fields, int index, String fieldName, int lineNum) {
        try {
            if (isEmptyField(fields, index)) return null;
            return Double.parseDouble(fields[index].trim());
        } catch (NumberFormatException e) {
            System.err.printf("[WARN] Erreur de parsing double pour '%s' ligne %d: '%s'%n",
                    fieldName, lineNum, getSafe(fields, index));
            return null;
        }
    }

    private static boolean isEmptyField(String[] fields, int index) {
        return fields.length <= index || fields[index] == null || fields[index].trim().isEmpty();
    }

    private static String getSafe(String[] fields, int index) {
        return (fields.length > index && fields[index] != null) ? fields[index] : "null";
    }
}