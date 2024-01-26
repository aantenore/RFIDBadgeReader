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

import java.util.Objects;
import java.util.function.Function;

public class BadgeApp {
    public static void main(String[] args) throws Exception {
        while (true) {
            try {
                start();
                while (true) {
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Il programma si riavvierà automaticamente tra 10 secondi.");
                Thread.sleep(10000);
            }
        }
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
            System.out.println("Il programma si chiuderà automaticamente tra 10 secondi.");
            Thread.sleep(10000);
            System.exit(-1);
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
}