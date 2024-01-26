package util;

import org.usb4java.*;

import java.util.HashMap;
import java.util.Map;

public class DeviceUtils {

    public static Context context;
    public static int contextInitialized;

    public static String getDeviceId(Device device) {
        return getDeviceId(getVendorId(device), getProductId(device));
    }

    public static String getDeviceId(String vendorId, String productId) {
        return String.format("%s:%s", vendorId, productId);
    }

    public static String getDeviceId(short vendorId, short productId) {
        return String.format("%04X:%04X", vendorId, productId);
    }

    public static short getVendorId(Device device) {
        DeviceDescriptor descriptor = getDeviceDescriptor(device);
        return descriptor.idVendor();
    }

    public static short getProductId(Device device) {
        DeviceDescriptor descriptor = getDeviceDescriptor(device);
        return descriptor.idProduct();
    }

    private static DeviceDescriptor getDeviceDescriptor(Device device) {
        DeviceDescriptor descriptor = new DeviceDescriptor();
        int result = LibUsb.getDeviceDescriptor(device, descriptor);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to read device descriptor", result);
        }
        return descriptor;
    }

    private static void initContext() {
        if (context == null) {
            contextInitialized = LibUsb.init(context);
            LibUsb.setOption(context, LibUsb.OPTION_USE_USBDK);
        }
        if (contextInitialized != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to initialize USB context", contextInitialized);
        }
    }

    public static Context getContext() {
        initContext();
        return context;
    }

    public static Map<String, String> decodingMap = new HashMap() {{
        put("30", "1");
        put("31", "2");
        put("32", "3");
        put("33", "4");
        put("34", "5");
        put("35", "6");
        put("36", "7");
        put("37", "8");
        put("38", "9");
        put("39", "0");
        put("40", "\n");
    }};
}
