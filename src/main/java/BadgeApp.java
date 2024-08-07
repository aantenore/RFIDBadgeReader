import component.Decoder;
import component.DeviceManager;
import component.EventManager;
import component.WriterManager;
import component.impl.Usb4JavaDeviceManagerImpl;
import component.impl.Usb4JavaEventManagerImpl;
import component.impl.XlsxWithTimestampDecoderImpl;
import component.impl.XlsxWriterImpl;
import dto.InputDecoded;
import dto.InputEncoded;
import lombok.SneakyThrows;
import org.usb4java.Device;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.TimerTask;
import java.util.function.Function;

public class BadgeApp {

    static final String LOCK_FILE_PATH = "badgeApp.lock";
    private static FileChannel lockChannel;

    public static void main(String[] args) throws Exception {

        boolean trayIconMounted = false;

        Runtime.getRuntime().addShutdownHook(new Thread(BadgeApp::releaseLock));

        if (!acquireLock()) {
            System.out.println("L'applicazione è già in esecuzione.");
            System.exit(0);
            return;
        }

        while (true) {
            try {
                boolean test = false;
                if (test) {
                    testWriteToXlsx();
                } else {
                    if (!trayIconMounted && SystemTray.isSupported()) {
                        Image iconImage = Toolkit.getDefaultToolkit().getImage(BadgeApp.class.getResource("icon.png"));
                        PopupMenu popupMenu = new PopupMenu();

                        JTextArea textArea = new JTextArea(30, 60);
                        textArea.setEditable(false);
                        textArea.setFont(new Font("Arial", Font.PLAIN, 14));
                        PrintStream printStream = new PrintStream(new ByteArrayOutputStream() {
                            @Override
                            @SneakyThrows
                            public synchronized void flush() {
                                textArea.append(toString(StandardCharsets.UTF_8.name()));
                                super.reset();
                            }
                        }, true, StandardCharsets.UTF_8.name());
                        System.setOut(printStream);
                        MenuItem viewLogItem = new MenuItem("Visualizza log");
                        viewLogItem.addActionListener(e -> showLogs(textArea));
                        popupMenu.add(viewLogItem);

                        MenuItem closeItem = new MenuItem("Chiudi Lettore Badge");
                        closeItem.addActionListener(e -> System.exit(0));
                        popupMenu.add(closeItem);

                        TrayIcon trayIcon = new TrayIcon(iconImage, "Lettore badge", popupMenu);
                        trayIcon.setImageAutoSize(true);
                        SystemTray tray = SystemTray.getSystemTray();
                        tray.add(trayIcon);
                        trayIconMounted = true;
                    }
                    start();
                }
                while (true) {}
            } catch (Exception e) {
                e.printStackTrace(System.out);
                //System.out.println("Il programma si chiuderà automaticamente tra 10 secondi.");
                System.out.println("Riavvio in corso... \n Errore: \n");
                e.printStackTrace(System.out);
                Thread.sleep(5000);
            }
        }
    }

    @SneakyThrows
    private static void testWriteToXlsx() {
        WriterManager<InputDecoded> writerManager = new XlsxWriterImpl();
        new java.util.Timer().schedule(new TimerTask() {
            @SneakyThrows
            @Override
            public void run() {
                writerManager.write(new InputDecoded("Test", LocalDateTime.now(), null));
            }
        }, 3000L, 10000L);
    }

    @SneakyThrows
    private static void showLogs(JTextArea textArea) {
        JScrollPane scrollPane = new JScrollPane(textArea);
        JFrame logFrame = new JFrame("Log");
        logFrame.add(scrollPane);
        logFrame.pack();
        logFrame.setVisible(true);
        logFrame.setIconImage(Toolkit.getDefaultToolkit().getImage(BadgeApp.class.getResource("icon.png")));

    }

    private static void start() throws Exception {
        DeviceManager<Device> deviceManager = new Usb4JavaDeviceManagerImpl();
        EventManager<Device>  eventManager = new Usb4JavaEventManagerImpl();
        Decoder decoder = new XlsxWithTimestampDecoderImpl();
        WriterManager<InputDecoded> writerManager = new XlsxWriterImpl();
        //deviceManager.showDevices();
        //List<Device> devices = deviceManager.getDevices();
        //for (Device device : devices) {
        Device device = deviceManager.getDevice();
        if(Objects.isNull(device)) {
            System.out.println("Nessun lettore trovato controlla di averlo collegato correttamente e riprova.");
            System.out.println("Ricorda che il vendorId e il productId sono configurati nel file files.properties sotto la chiave scanner.vendorId e scanner.productId");
            //System.out.println("Il programma si chiuderà automaticamente tra 5 secondi.");
            System.out.println("Riavvio in corso... \n");
            //System.exit(-1);
        }
        eventManager.registerHandlingForDevice(
                device,
                input -> {
                    decoder
                            .decode(new InputEncoded(input))
                            .map(new Function<InputDecoded, Object>() {
                                @SneakyThrows
                                @Override
                                public Object apply(InputDecoded inputDecoded) {
                                    return writerManager.write(inputDecoded);
                                }
                            });
                }
        );
        //}

    }

    private static boolean acquireLock() {
        try {
            File file = new File(LOCK_FILE_PATH);
            if (!file.exists()) {
                file.createNewFile();
            }
            lockChannel = new RandomAccessFile(file, "rw").getChannel();
            return lockChannel.tryLock() != null;
        } catch (IOException e) {
            e.printStackTrace(System.out);
            return false;
        }
    }

    private static void releaseLock() {
        try {
            if (lockChannel != null) {
                lockChannel.close();
                File file = new File(LOCK_FILE_PATH);
                if (file.exists()) {
                    file.delete();
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}