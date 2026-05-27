package com.buscorp.employee;

import android.content.Context;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.buscorp.employee.core.audit.AuditChainHasher;
import com.buscorp.employee.core.db.AppDatabase;
import com.buscorp.employee.core.db.TicketDao;
import com.buscorp.employee.core.db.TicketEntity;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public class RemittanceViewModel extends ViewModel {

    private static final String TAG = "RemittanceViewModel";
    
    private final TicketDao ticketDao;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    private final MutableLiveData<RemittanceState> state = new MutableLiveData<>();
    private final MutableLiveData<ExportState> exportState = new MutableLiveData<>();
    
    private List<TicketEntity> currentTickets;

    @Inject
    public RemittanceViewModel(@ApplicationContext Context context) {
        this.context = context;
        this.ticketDao = AppDatabase.getInstance(context).ticketDao();
    }
    
    public LiveData<RemittanceState> getState() { return state; }
    public LiveData<ExportState> getExportState() { return exportState; }
    
    public void loadShiftData() {
        executor.execute(() -> {
            try {
                currentTickets = ticketDao.getAllTickets();
                
                double totalCash = 0;
                double totalQr = 0; // Simulated
                int count = currentTickets.size();
                
                for (TicketEntity t : currentTickets) {
                    // For this phase, assume all Room tickets are cash
                    totalCash += t.getFare();
                }
                
                // Add some dummy QR data for chart comparison
                totalQr = totalCash * 0.3; // 30% of sales are QR
                
                state.postValue(new RemittanceState(totalCash, totalQr, count));
            } catch (Exception e) {
                Log.e(TAG, "Error loading shift data", e);
            }
        });
    }

    public void generateReports() {
        if (currentTickets == null) return;
        
        executor.execute(() -> {
            try {
                exportState.postValue(ExportState.GENERATING);
                
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String pdfName = "ShiftRemittance_" + timestamp + ".pdf";
                String excelName = "ShiftTickets_" + timestamp + ".xlsx";

                generatePdf(pdfName, currentTickets);
                generateExcel(excelName, currentTickets);
                
                exportState.postValue(ExportState.SUCCESS);
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                exportState.postValue(ExportState.ERROR);
            }
        });
    }
    
    private void generatePdf(String fileName, List<TicketEntity> tickets) throws Exception {
        OutputStream fos = getOutputStreamForFile(fileName, "application/pdf");
        if (fos == null) throw new Exception("Could not create MediaStore output stream");

        PdfWriter writer = new PdfWriter(fos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        
        // 1. Decode Logo
        Bitmap logoBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.asset_pos_bus_logo);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        logoBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        ImageData imageData = ImageDataFactory.create(stream.toByteArray());
        Image logo = new Image(imageData).scaleToFit(150, 150).setTextAlignment(TextAlignment.CENTER);
        document.add(logo);
        
        // 2. Header
        document.add(new Paragraph("Bus Corp. - Shift Remittance Report").setBold().setFontSize(18));
        document.add(new Paragraph("Date: " + new Date().toString()));
        document.add(new Paragraph("Total Tickets: " + tickets.size()));
        
        double totalCash = tickets.stream().mapToDouble(TicketEntity::getFare).sum();
        document.add(new Paragraph(String.format("Total Cash Remittance: Php %.2f", totalCash)).setBold());
        
        document.add(new Paragraph("\n"));
        document.add(new Paragraph("Conductor Signature: ______________________"));
        
        // 3. Security Hash (AuditChainHasher)
        JSONObject payload = new JSONObject();
        payload.put("total_cash", totalCash);
        payload.put("ticket_count", tickets.size());
        payload.put("timestamp", System.currentTimeMillis());
        
        String hash = AuditChainHasher.hash("", payload.toString());
        document.add(new Paragraph("\n\n-- AUDIT HASH --\n" + hash).setFontSize(8));

        document.close();
    }
    
    private void generateExcel(String fileName, List<TicketEntity> tickets) throws Exception {
        OutputStream fos = getOutputStreamForFile(fileName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        if (fos == null) throw new Exception("Could not create MediaStore output stream");

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Tickets");
        
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Ticket ID");
        header.createCell(1).setCellValue("Origin");
        header.createCell(2).setCellValue("Destination");
        header.createCell(3).setCellValue("Fare");
        header.createCell(4).setCellValue("Type");
        
        int rowNum = 1;
        for (TicketEntity ticket : tickets) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(ticket.getId());
            row.createCell(1).setCellValue(ticket.getOrigin());
            row.createCell(2).setCellValue(ticket.getDestination());
            row.createCell(3).setCellValue(ticket.getFare());
            row.createCell(4).setCellValue(ticket.getPassengerType());
        }
        
        workbook.write(fos);
        workbook.close();
        fos.close();
    }
    
    private OutputStream getOutputStreamForFile(String fileName, String mimeType) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BusCorp");
        }
        
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
        if (uri != null) {
            return resolver.openOutputStream(uri);
        }
        return null;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }

    // --- States ---
    public static class RemittanceState {
        public final double totalCash, totalQr;
        public final int ticketCount;
        public RemittanceState(double cash, double qr, int count) {
            this.totalCash = cash; this.totalQr = qr; this.ticketCount = count;
        }
    }
    
    public enum ExportState { GENERATING, SUCCESS, ERROR }
}
