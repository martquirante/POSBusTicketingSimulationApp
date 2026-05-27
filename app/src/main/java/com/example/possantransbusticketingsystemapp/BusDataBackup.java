package com.example.possantransbusticketingsystemapp;

import android.os.Environment;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class BusDataBackup extends BaseActivity {

    private static final String BACKUP_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/PossanTransBackup";
    private static final String FILE_NAME = "bus_session_backup.txt";

    // --- 1. SAVE ALL DATA (Ngayon kasama na ang Ticket Info) ---
    public static void saveFullBackup(String bus, String driver, String conductor,
                                      int totalPax, int cash, int gcash,
                                      String lastTicketID, String lastLoop, String lastPType) {

        File folder = new File(BACKUP_PATH);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File file = new File(folder, FILE_NAME);
        try {
            FileWriter writer = new FileWriter(file);
            // Format: BUS,DRIVER,CONDUCTOR,PAX,CASH,GCASH,TICKET_ID,LOOP,PTYPE
            // Gamit tayo ng separator na safe (Example: | or ,)
            writer.write(bus + "," + driver + "," + conductor + "," +
                    totalPax + "," + cash + "," + gcash + "," +
                    lastTicketID + "," + lastLoop + "," + lastPType);

            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- 2. LOAD DATA ---
    public static String[] readBackup() {
        File file = new File(BACKUP_PATH, FILE_NAME);
        if (!file.exists()) return null;

        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            reader.close();

            if (line != null) {
                return line.split(",");
                // Inaasahan natin na 9 items na ang laman nito ngayon
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean hasBackup() {
        File file = new File(BACKUP_PATH, FILE_NAME);
        return file.exists();
    }

    public static void deleteBackup() {
        File file = new File(BACKUP_PATH, FILE_NAME);
        if (file.exists()) {
            file.delete();
        }
    }
}