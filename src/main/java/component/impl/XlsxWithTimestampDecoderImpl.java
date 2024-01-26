package component.impl;

import component.Decoder;
import dto.InputDecoded;
import dto.InputEncoded;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import util.CommonUtil;

import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.util.*;

public class XlsxWithTimestampDecoderImpl implements Decoder {

    private final static String decodingFileNameProperty = "employees.codes.file.name";
    private final static String xlsxHeaderProperty = "employees.codes.header";
    private Map<String, String> decodingMap;
    private String decodingfileName;

    public XlsxWithTimestampDecoderImpl() throws Exception {
        this.decodingMap = new HashMap();
        loadDecodingMap(loadProps());
    }

    private Properties loadProps() throws Exception {
        Properties props = new Properties();
        try(FileInputStream fileInputStream = new FileInputStream(CommonUtil.getAbsolutePath("files.properties")))   {
            props.load(fileInputStream);
        }
        return props;
    }

    private void loadDecodingMap(Properties props) throws Exception {
        decodingfileName = CommonUtil.getAbsolutePath(props.getProperty(decodingFileNameProperty));
        boolean xlsxHeader = Boolean.parseBoolean(props.getProperty(xlsxHeaderProperty));

        Workbook decodingXlsx = new XSSFWorkbook(new FileInputStream(decodingfileName));
        Sheet sheet = decodingXlsx.getSheetAt(0);
        Iterator<Row> rowIterator = sheet.iterator();

        // Skip header se presente
        if (xlsxHeader && rowIterator.hasNext()) {
            rowIterator.next();
        }

        DataFormatter dataFormatter = new DataFormatter();

        // Carica in decodingMap i valori del file, la chiave viene paddata a 20 con 0 a sinistra
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            Cell keyCell = row.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            Cell valueCell = row.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            String key = formatKey(dataFormatter.formatCellValue(keyCell).trim());
            String value = dataFormatter.formatCellValue(valueCell).trim();
            decodingMap.put(key, value);
        }

        //System.out.println("Decoding map loaded" + decodingMap);
    }

    private String formatKey(String key) {
        return String.format("%" + 20 + "s", key).replace(' ', '0');
    }

    @Override
    public Optional<InputDecoded> decode(InputEncoded code) {
        LocalDateTime now = LocalDateTime.now();
        System.out.println("Carta numero: " + code.getCode() + " rilevata alle: " + now);
        String codeString = formatKey((String) code.getCode());
        String decodedCode = decodingMap.get(codeString);
        //System.out.println("Decoded code: " + decodedCode);

        if (Objects.nonNull(decodedCode)) {
            System.out.println("Trovata corrispondenza: " + code.getCode() + " -> " + decodedCode);
            return Optional.of(new InputDecoded(decodedCode, now, null));
        } else {
            System.out.println("Nessuna corrispondenza trovata per: " + code.getCode() + " censisci la carta nel file " + decodingfileName);
            return Optional.empty();
        }
    }
}
