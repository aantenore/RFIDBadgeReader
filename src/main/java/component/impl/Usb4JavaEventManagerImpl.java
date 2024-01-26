package component.impl;

import component.EventManager;
import jxl.common.Assert;
import org.usb4java.Device;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;
import util.DeviceUtils;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.Consumer;

public class Usb4JavaEventManagerImpl implements EventManager<Device> {

    private static final int CARD_CODE_LENGTH = 10;
    private static final int SINGLE_CODE_ITEM_BYTE_SIZE = 16;
    private static final int READING_POLL_TIME_MS = 5000;

    @Override
    public void registerHandlingForDevice(Device device, Consumer doOnInput) {
        Listener listener = new Listener(doOnInput);
        listener.startListening(getDeviceHandle(device));
    }

    private DeviceHandle getDeviceHandle(Device device) {
        DeviceHandle handle = new DeviceHandle();
        int result = LibUsb.open(device, handle);
        boolean handleOpened = result == LibUsb.SUCCESS;
        if (handleOpened) {
            return handle;
        }

        throw new LibUsbException("Unable to read device descriptor", result);
    }

    private static class Listener {

        private final Consumer doOnInput;
        private StringBuilder inputBuffer;

        public Listener(Consumer doOnInput) {
            this.doOnInput = doOnInput;
            this.inputBuffer = new StringBuilder();
        }

        public void startListening(DeviceHandle handle) {
            /*Listener thisListener = this;
            SwingUtilities.invokeLater(new Runnable() {
                @SneakyThrows
                @Override
                public void run() {
                    JFrame frame = new JFrame("KeyListener Example");
                    System.setProperty("sun.java2d.noddraw", "true");
                    frame.setBounds(0, 0, 1, 1);

                    frame.setUndecorated(true);
                    frame.setLocationRelativeTo(null);

                    GlobalScreen.setEventDispatcher(new VoidDispatchService());
                    GlobalScreen.registerNativeHook();
                    Logger loggy = Logger.getLogger(GlobalScreen.class.getPackage().getName());
                    loggy.setLevel(Level.SEVERE);
                    GlobalScreen.addNativeKeyListener(thisListener);

                    frame.setFocusable(false);
                    frame.setVisible(false);
                }
            });*/
            System.out.println("Connesso al lettore!");
            int inputByteLength = SINGLE_CODE_ITEM_BYTE_SIZE * (CARD_CODE_LENGTH+1);
            IntBuffer bufferSize = IntBuffer.allocate(1);
            ByteBuffer buffer = ByteBuffer.allocateDirect(inputByteLength);
            while (true) {
                int result = LibUsb.bulkTransfer(handle, (byte) 0x81, buffer, bufferSize, READING_POLL_TIME_MS);
                if (result == LibUsb.SUCCESS && bufferSize.get() > 0) {
                    // Stampa l'input
                    int bufferSizeInt = bufferSize.get(0);
                    int groups = bufferSizeInt / SINGLE_CODE_ITEM_BYTE_SIZE;
                    for (int i = 0; i < groups; i++) {
                        byte[] groupBytes = new byte[SINGLE_CODE_ITEM_BYTE_SIZE];
                        buffer.get(groupBytes);
                        String decodedSingleInput = DeviceUtils.decodingMap.get(String.valueOf(groupBytes[2]));
                        if (decodedSingleInput==null||"\n".equals(decodedSingleInput)) {
                            break;
                        }
                        inputBuffer.append(decodedSingleInput);
                    }
                    doOnInput.accept(inputBuffer.toString());
                    inputBuffer.setLength(0);
                    bufferSize = IntBuffer.allocate(1);
                    buffer = ByteBuffer.allocateDirect(inputByteLength);
                } else if (result == LibUsb.ERROR_TIMEOUT) {
                    // Timeout, puoi gestirlo come preferisci
                    // System.err.println("Timeout while waiting for data.");
                } else {
                    throw new LibUsbException("Unable to perform bulk transfer", result);
                }
            }
        }

/*
        @Override
        public void nativeKeyTyped(NativeKeyEvent e) {
            if (inputBuffer.length()==0) {
                new Timer().schedule(new TimerTask() {
                    @SneakyThrows
                    @Override public void run() {
                        synchronized (inputBuffer) {
                            inputBuffer.setLength(0);
                        }
                    }
                }, 200);
            }
            if (e.getKeyCode() == NativeKeyEvent.VC_ENTER) {
                String code = inputBuffer.toString();
                int inputLength = code.length();
                if (inputLength > 10) {
                    //System.out.println("Code: " + code);
                    //System.out.println("inputLength: " + inputLength);
                    //System.out.println("doOnInput: " + doOnInput);
                    System.out.println("code.substring(inputLength-11, inputLength-1): " + code.substring(inputLength-11, inputLength-1));
                    doOnInput.accept(code.substring(inputLength-11, inputLength-1));
                    // Reinizializza il buffer per la successiva input
                    inputBuffer.setLength(0);
                }
            }
            consume(e);
            //System.out.println("nativeKeyTyped");
        }

        @Override
        public void nativeKeyPressed(NativeKeyEvent e) {
           //System.out.println("nativeKeyPressed");
            //inputBuffer.append(e.getKeyChar());
        }

        @Override
        public void nativeKeyReleased(NativeKeyEvent e) {
            //System.out.println("nativeKeyReleased");
        }

        private class SwingExecutorService extends AbstractExecutorService {
            private EventQueue queue;

            public SwingExecutorService() {
                queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
            }

            @Override
            public void shutdown() {
                queue = null;
            }

            @Override
            public List<Runnable> shutdownNow() {
                return new ArrayList(0);
            }

            @Override
            public boolean isShutdown() {
                return queue == null;
            }

            @Override
            public boolean isTerminated() {
                return queue == null;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                return true;
            }

            @Override
            public void execute(Runnable r) {
                EventQueue.invokeLater(r);
            }
        }
    }

    private static void consume(NativeKeyEvent e) {
        try {
            Field f = NativeInputEvent.class.getDeclaredField("reserved");
            f.setAccessible(true);
            f.setShort(e, (short) 0x01);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
*/

    }
}
