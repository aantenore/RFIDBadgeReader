package component.impl;

import component.DeviceManager;
import org.apache.commons.compress.utils.Lists;
import org.usb4java.*;
import util.CommonUtil;
import util.DeviceUtils;

import java.io.FileInputStream;
import java.util.List;
import java.util.Properties;
import java.util.function.BiConsumer;

public class Usb4JavaDeviceManagerImpl implements DeviceManager<Device> {

    private DeviceList deviceList;
    private String deviceId;

    public Usb4JavaDeviceManagerImpl() throws Exception {
        Properties props = loadProps();
        String vendorId = props.getProperty("scanner.vendorId", "FFFF");
        String productId = props.getProperty("scanner.productId", "0035");
        deviceId = DeviceUtils.getDeviceId(vendorId, productId);
    }

    private Properties loadProps() throws Exception {
        Properties props = new Properties();
        try(FileInputStream fileInputStream = new FileInputStream(CommonUtil.getAbsolutePath("files.properties")))   {
            props.load(fileInputStream);
        }
        return props;
    }

    @Override
    public Device getDevice() {
        return findAndKeepDevice(deviceId, getDeviceList());
    }

    @Override
    public void showDevices() {
        doOnDevices((listOfDevices, deviceList) -> {
            //try {
                // Itera attraverso i dispositivi e stampa le informazioni
                for (Device device: listOfDevices) {
                    System.out.println("Device: " + DeviceUtils.getDeviceId(device));
                }
            /*} finally {
                // Rilascia la lista dei dispositivi
                LibUsb.freeDeviceList(deviceList, true);
            }*/
        });
    }

    private void doOnDevices(BiConsumer<List<Device>, DeviceList> doAction) {
        // Ottieni la lista dei dispositivi USB
        doAction.accept(getDevices(), getDeviceList());
    }

    private Device findAndKeepDevice(String deviceId, DeviceList list) {
        Device deviceToKeep = null;
        // Itera sulla lista dei dispositivi per trovare quello desiderato
        System.out.println("Cerco di connettermi al lettore...");
        for (Device device : list) {
            DeviceDescriptor descriptor = new DeviceDescriptor();
            int result = LibUsb.getDeviceDescriptor(device, descriptor);
            if (result != LibUsb.SUCCESS) {
                throw new LibUsbException("Unable to read device descriptor", result);
            }

            if (DeviceUtils.getDeviceId(device).equals(deviceId)) {
                deviceToKeep = device;
                System.out.println("Lettore " + deviceId + " trovato!");
            } /*else {
                // Rilascia tutti gli altri dispositivi
                DeviceHandle handle = new DeviceHandle();
                boolean handleOpened = LibUsb.open(device, handle) == LibUsb.SUCCESS;
                if (handleOpened) {
                    LibUsb.releaseInterface(handle, 0);
                    LibUsb.close(handle);
                }
            }*/
        }
        return deviceToKeep;
    }

    @Override
    public List<Device> getDevices() {
        return Lists.newArrayList(getDeviceList().iterator());
    }

    private DeviceList getDeviceList() {
        if (deviceList == null) {
            // Ottieni la lista dei dispositivi USB
            deviceList = new DeviceList();
            int result = LibUsb.getDeviceList(DeviceUtils.getContext(), deviceList);
            if (result < 0) {
                throw new LibUsbException("Unable to get device list", result);
            }
            //LibUsb.freeDeviceList(deviceList, true);
        }
        return deviceList;
    }
}
