package com.example.possantransbusticketingsystemapp.utils;

import android.os.Environment;
import com.example.possantransbusticketingsystemapp.models.Ticket;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
public class FileManager {

    private static final String ROOT_FOLDER_NAME = "SANTRANS POS FILES";

    // Ito ang magse-save ng receipt sa specific folder structure na request mo
    public boolean saveTicketReceipt(Ticket ticket, boolean isReverseLoop) {

        // 1. Identify Folders
        String routeFolder = isReverseLoop ? "ST.CRUZ TO FVR" : "FVR TO ST.CRUZ";
        String paymentFolder = ticket.getPaymentMethod().toUpperCase(); // "CASH" or "GCASH"

        // 2. Build Path: Documents / SANTRANS POS FILES / CUSTOMER TICKET / ROUTE / PAYMENT
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File targetDir = new File(documentsDir, ROOT_FOLDER_NAME + File.separator +
                "CUSTOMER TICKET" + File.separator +
                routeFolder + File.separator +
                paymentFolder);

        // 3. Create folders if not exists
        if (!targetDir.exists()) {
            if (!targetDir.mkdirs()) {
                return false; // Error creating folder
            }
        }

        // 4. Generate Filename
        // Format: time_date route.txt
        String fileName = ticket.getFormattedDateForFile() + " " + ticket.getOrigin() + " to " + ticket.getDestination() + ".txt";
        File receiptFile = new File(targetDir, fileName);

        // 5. Write Content
        try {
            FileWriter writer = new FileWriter(receiptFile);
            writer.append("================================\n");
            writer.append("      SANTRANS CORPORATION      \n");
            writer.append("================================\n");
            writer.append("Date: " + ticket.getFormattedDateForPrint() + "\n");
            writer.append("Serial #: MP071455\n");
            writer.append("Bus #: " + ticket.getBusNumber() + "\n");
            writer.append("Driver: " + ticket.getDriverName() + "\n");
            writer.append("Conductor: " + ticket.getConductorName() + "\n");
            writer.append("--------------------------------\n");
            writer.append("ROUTE: " + ticket.getOrigin() + " -> " + ticket.getDestination() + "\n");
            writer.append("TYPE: " + ticket.getPassengerType() + "\n");
            writer.append("PAYMENT: " + ticket.getPaymentMethod() + "\n");
            writer.append("--------------------------------\n");
            writer.append("TOTAL AMOUNT: PHP " + String.format("%.2f", ticket.getPrice()) + "\n");
            writer.append("================================\n");
            writer.flush();
            writer.close();
            return true; // Success
        } catch (IOException e) {
            e.printStackTrace();
            return false; // Failed
        }
    }


}
