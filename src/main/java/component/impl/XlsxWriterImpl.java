package component.impl;

import component.WriterManager;
import dto.InputDecoded;
import lombok.SneakyThrows;
import org.apache.poi.hssf.record.crypto.Biff8EncryptionKey;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import util.CommonUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

public class XlsxWriterImpl implements WriterManager<InputDecoded> {

    private static final String outputFileNameProperty = "employees.output.file.name";
    private static final String outputFilePasswordProperty = "employees.output.file.passw";

    private String outputFileName;
    private String password;
    private Queue<InputDecoded> pendingWrites;
    private int failures;
    private Workbook workbook;

    public XlsxWriterImpl() throws Exception {
        this.pendingWrites = new LinkedList();
        this.outputFileName = loadProps().getProperty(outputFileNameProperty);
        this.password = loadProps().getProperty(outputFilePasswordProperty, "password");
        this.failures = 0;
    }

    private Properties loadProps() throws Exception {
        Properties props = new Properties();
        try(FileInputStream fileInputStream = new FileInputStream(CommonUtil.getAbsolutePath("files.properties")))   {
            props.load(fileInputStream);
        }
        return props;
    }

    @SneakyThrows
    private Workbook getOrCreateWorkbook(String absoluteFileName) {
        File file = new File(absoluteFileName);
        Workbook newWorkbook;

        if (!file.exists() || file.length() == 0) {
            //System.out.println("File is empty, returning a new workbook");
            newWorkbook = new HSSFWorkbook();
            writeToFile(file, newWorkbook);
        } else {
            //You are calling the part of POI that deals with OLE2 Office Documents. You need to call a different part of POI to process this data (eg XSSF instead of HSSF)
            //System.out.println("File is not empty, returning the existing workbook");
            try (
                    FileInputStream fileInputStream = new FileInputStream(file);
            ) {
                newWorkbook = WorkbookFactory.create(fileInputStream, password);
            }
        }

        return newWorkbook;
    }

    private static void initWorkbook(Workbook newWorkbook) {
        Sheet sheet = newWorkbook.getNumberOfSheets()>0?
                newWorkbook.getSheetAt(0):
                newWorkbook.createSheet("-");

        if (sheet.getLastRowNum() < 0) {
            Row headerRow = sheet.createRow(0);
            int columnIndex = 0;

            Cell employeeCell = headerRow.createCell(columnIndex++);
            employeeCell.setCellValue("Codice");

            Cell dateRow = headerRow.createCell(columnIndex++);
            dateRow.setCellValue("Data");

            Cell hourRow = headerRow.createCell(columnIndex++);
            hourRow.setCellValue("Orario");

            Cell whenCellDay = headerRow.createCell(columnIndex++);
            whenCellDay.setCellValue("Giorno");

            Cell whenCellMonth = headerRow.createCell(columnIndex++);
            whenCellMonth.setCellValue("Mese");

            Cell whenCellYear = headerRow.createCell(columnIndex++);
            whenCellYear.setCellValue("Anno");

            Cell whenCellHours = headerRow.createCell(columnIndex++);
            whenCellHours.setCellValue("Ore");

            Cell whenCellMinutes = headerRow.createCell(columnIndex++);
            whenCellMinutes.setCellValue("Minuti");

            Cell whenCellSeconds = headerRow.createCell(columnIndex++);
            whenCellSeconds.setCellValue("Secondi");
        }
    }

    private void writeToFile(File file, Workbook workbook) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            Biff8EncryptionKey.setCurrentUserPassword(password);
            workbook.write(fileOutputStream);
        }
    }

    private String getCurrentOutputFileName() {
        return CommonUtil.getAbsolutePath(
            outputFileName.replace(
        "{date}",
                getToday()
            )
        );
    }

    private String getToday() {
        return new SimpleDateFormat("yyyyMMdd").format(new Date());
    }

    @Override
    public Void write(InputDecoded inputDecoded) {
        inputDecoded.setFileName(getCurrentOutputFileName());
        putInputInQueue(inputDecoded);
        return null;
    }

    private void putInputInQueue(InputDecoded inputDecoded) {
        pendingWrites.add(inputDecoded);
        new Timer().schedule(new TimerTask() {
            @SneakyThrows
            @Override public void run() {
                writeLogic();
            }

            private void writeLogic() {
                try {
                    if(!pendingWrites.isEmpty()) {
                        InputDecoded inputToWrite = pendingWrites.peek();
                        String fileName = inputToWrite.getFileName();
                        workbook = getOrCreateWorkbook(fileName);
                        synchronized (workbook) {
                            try(
                                FileOutputStream fileOut = new FileOutputStream(fileName)
                            ) {
                                FileLock lock = fileOut.getChannel().tryLock();
                                if (lock != null && !pendingWrites.isEmpty()) {
                                    failures = 0;
                                    initWorkbook(workbook);
                                    addInputToExcel(workbook, inputToWrite);
                                    lock.release();
                                    writeToFile(new File(fileName), workbook);
                                    System.out.println("Scritto su file " + fileName + " il codice rilevato: " + inputToWrite.getCode());
                                    //System.out.println("Salvato file " + fileName);
                                    pendingWrites.poll();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                    failures ++;
                    new Timer().schedule(new TimerTask() {
                        @SneakyThrows
                        @Override public void run() {
                            writeLogic();
                        }
                    }, new Double(Math.pow(2, failures)).longValue() * 1000);
                    System.out.println("Errore durante la scrittura su file (forse devi chiudere il file), metto la scrittura in coda. Riprovo a scrivere tra: " + new Double(Math.pow(2, failures)).intValue() + " secondi");
                } finally {
                    cancel();
                }
            }
        }, new Double(Math.pow(2, failures)).longValue() * 1000, new Double(Math.pow(2, failures)).longValue() * 1000);
        //System.out.println("Il file Excel è già aperto da un altro processo. Aspetto");
    }

    private void addInputToExcel(Workbook workbook, InputDecoded inputDecoded) {
        Sheet sheet = workbook.getSheetAt(0);

        for (int i = sheet.getLastRowNum(); i >= 0; i--) {
            Row row = sheet.getRow(i);
            if (row != null && row.getCell(0) != null && row.getCell(0).getCellType() == CellType.BLANK) {
                sheet.removeRow(row);
            }
        }

        int lastRowNum = sheet.getLastRowNum();

        int columnIndex = 0;
        Row newRow = sheet.createRow(lastRowNum + 1);

        Cell employeeCell = newRow.createCell(columnIndex++);
        employeeCell.setCellValue(String.valueOf(inputDecoded.getCode()));

        LocalDateTime when = inputDecoded.getWhen();

        Cell dateRow = newRow.createCell(columnIndex++);
        dateRow.setCellValue(when.getDayOfMonth() + "/" + when.getMonthValue() + "/" + when.getYear());

        Cell hourRow = newRow.createCell(columnIndex++);
        hourRow.setCellValue(when.getHour() + ":" + when.getMinute());

        Cell whenCellDay = newRow.createCell(columnIndex++);
        whenCellDay.setCellValue(when.getDayOfMonth());

        Cell whenCellMonth = newRow.createCell(columnIndex++);
        whenCellMonth.setCellValue(when.getMonthValue());

        Cell whenCellYear = newRow.createCell(columnIndex++);
        whenCellYear.setCellValue(when.getYear());

        Cell whenCellHours = newRow.createCell(columnIndex++);
        whenCellHours.setCellValue(when.getHour());

        Cell whenCellMinutes = newRow.createCell(columnIndex++);
        whenCellMinutes.setCellValue(when.getMinute());

        Cell whenCellSeconds = newRow.createCell(columnIndex++);
        whenCellSeconds.setCellValue(when.getSecond());
    }

}
