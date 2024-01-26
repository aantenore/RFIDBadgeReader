package component;

import java.util.List;

public interface DeviceManager<T> {
    T getDevice();
    void showDevices();
    List<T> getDevices();
}
