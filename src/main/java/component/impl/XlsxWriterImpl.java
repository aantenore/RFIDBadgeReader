package component.impl;

import component.WriterManager;
import dto.InputDecoded;
import lombok.SneakyThrows;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import util.CommonUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.channels.FileLock;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

public class XlsxWriterImpl implements WriterManager<InputDecoded> {

    private static final String outputFileNameProperty = "employees.output.file.name";
    private static final String outputFilePasswordProperty = "employees.output.file.passw";

    private String outputFileName;
    private String password;
    private String today;
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

    private Workbook getOrCreateWorkbook(String absoluteFileName) throws Exception {
        File file = new File(absoluteFileName);
        if (!file.exists()) {
            file.createNewFile();
            //System.out.println("File does not exist, creating");
            Workbook newWorkbook = new XSSFWorkbook();
            newWorkbook.createSheet("-");
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                newWorkbook.write(fileOutputStream);
            }
            setPassword(absoluteFileName);
            return newWorkbook;
        } else {
            //System.out.println("File exists, returning");
            if (file.length() == 0) {
                //System.out.println("File is empty, returning a new workbook");
                Workbook newWorkbook = new XSSFWorkbook();
                newWorkbook.createSheet("-");
                return newWorkbook;
            } else {
                //You are calling the part of POI that deals with OLE2 Office Documents. You need to call a different part of POI to process this data (eg XSSF instead of HSSF)
                //System.out.println("File is not empty, returning the existing workbook");
                try (
                    FileInputStream fileInputStream = new FileInputStream(file);
                    POIFSFileSystem fs = new POIFSFileSystem(fileInputStream);
                ) {
                    EncryptionInfo info = new EncryptionInfo(fs);
                    Decryptor d = Decryptor.getInstance(info);
                    d.verifyPassword(password);
                    return new XSSFWorkbook(d.getDataStream(fs));
                }
            }
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
    public Void write(InputDecoded inputDecoded) throws Exception{
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
                                    lock.release();
                                    addInputToExcel(workbook, inputToWrite);
                                    System.out.println("Scritto su file " + fileName + " il codice rilevato: " + inputToWrite.getCode());
                                    workbook.write(fileOut);
                                    //System.out.println("Salvato file " + fileName);
                                    setPassword(fileName);
                                    pendingWrites.poll();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    failures ++;
                    new Timer().schedule(new TimerTask() {
                        @SneakyThrows
                        @Override public void run() {
                            writeLogic();
                        }
                    }, new Double(Math.pow(2, failures)).longValue() * 1000);
                    System.out.println("Errore durante la scrittura su file (forse devi chiudere il file), metto la scrittura in coda. Riprovo a scrivere tra: " + new Double(Math.pow(2, failures)).intValue() + " secondi");
                    //e.printStackTrace();
                } finally {
                    cancel();
                }
            }
        }, new Double(Math.pow(2, failures)).longValue() * 1000, new Double(Math.pow(2, failures)).longValue() * 1000);
        //System.out.println("Il file Excel è già aperto da un altro processo. Aspetto");
    }

    private void setPassword(String fileName) throws Exception {
        try(POIFSFileSystem fs=new POIFSFileSystem()) {
            EncryptionInfo info=new EncryptionInfo(EncryptionMode.agile);
            Encryptor enc=info.getEncryptor();
            enc.confirmPassword(password);
            try(OPCPackage opc=OPCPackage.open(new File(fileName), PackageAccess.READ_WRITE);
                OutputStream os=enc.getDataStream(fs)) {
                opc.save(os);
            }
            try (FileOutputStream fos=new FileOutputStream(fileName)){
                fs.writeFilesystem(fos);
            }
        }
    }

    private void addInputToExcel(Workbook workbook, InputDecoded inputDecoded) {
        Sheet sheet = workbook.getSheetAt(0);
        int lastRowNum = sheet.getLastRowNum();
        if (lastRowNum < 0) {
            lastRowNum++;
            Row headerRow = sheet.createRow(lastRowNum);
            Cell employeeCell = headerRow.createCell(0);
            employeeCell.setCellValue("Codice");

            Cell whenCellDay = headerRow.createCell(1);
            whenCellDay.setCellValue("Giorno");

            Cell whenCellMonth = headerRow.createCell(2);
            whenCellMonth.setCellValue("Mese");

            Cell whenCellYear = headerRow.createCell(3);
            whenCellYear.setCellValue("Anno");

            Cell whenCellHours = headerRow.createCell(4);
            whenCellHours.setCellValue("Ore");

            Cell whenCellMinutes = headerRow.createCell(5);
            whenCellMinutes.setCellValue("Minuti");

            Cell whenCellSeconds = headerRow.createCell(6);
            whenCellSeconds.setCellValue("Secondi");
        }

        Row newRow = sheet.createRow(lastRowNum + 1);

        Cell employeeCell = newRow.createCell(0);
        employeeCell.setCellValue(String.valueOf(inputDecoded.getCode()));

        LocalDateTime when = inputDecoded.getWhen();

        Cell whenCellDay = newRow.createCell(1);
        whenCellDay.setCellValue(when.getDayOfMonth());

        Cell whenCellMonth = newRow.createCell(2);
        whenCellMonth.setCellValue(when.getMonthValue());

        Cell whenCellYear = newRow.createCell(3);
        whenCellYear.setCellValue(when.getYear());

        Cell whenCellHours = newRow.createCell(4);
        whenCellHours.setCellValue(when.getHour());

        Cell whenCellMinutes = newRow.createCell(5);
        whenCellMinutes.setCellValue(when.getMinute());

        Cell whenCellSeconds = newRow.createCell(6);
        whenCellSeconds.setCellValue(when.getSecond());
    }

}
