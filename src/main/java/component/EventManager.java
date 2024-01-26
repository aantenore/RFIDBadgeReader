package component;

import java.util.function.Consumer;

public interface EventManager<T> {
    void registerHandlingForDevice(T device, Consumer action);
}
