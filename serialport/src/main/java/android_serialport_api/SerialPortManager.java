package android_serialport_api;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SerialPortManager {
    private final String path;
    private final int baudrate;
    private final int flags;
    private OpenListener openListener;
    private DataReceiveListener dataReceiveListener;

    private SerialPort serialPort;
    private InputStream is;
    private OutputStream os;
    private volatile boolean isOpen = false;
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if(msg.what == 0){
                byte[] data = (byte[]) msg.obj;
                if(dataReceiveListener != null) {
                    dataReceiveListener.dataReceive(data);
                }
            }
        }
    };

    private SerialPortManager(Builder builder) {
        path = builder.path;
        baudrate = builder.baudrate;
        flags = builder.flags;
        openListener = builder.openListener;
        dataReceiveListener = builder.dataReceiveListener;
    }

    public void sendString(String data){
        if(!TextUtils.isEmpty(data)) {
            sendData(data.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void sendData(byte[] data) {
        if(isOpen) {
            new SendThread(data, serialPort.getOutputStream()).start();
        }
    }

    public boolean isOpen() {
        return isOpen;
    }

    /**
     * Open the serial port.
     */
    public void open(){
        close();
        try {
            serialPort = new SerialPort(new File(path), baudrate, flags);
            is = serialPort.getInputStream();
            os = serialPort.getOutputStream();
            isOpen = true;
            if(openListener != null) {
                openListener.isOpen(true);
            }
            new ReceiveThread().start();
        } catch (Exception e) {
            isOpen = false;
            if(openListener != null) {
                openListener.isOpen(false);
            }
        }
    }

    /**
     * Close the serial port.
     */
    public void close(){
        try {
            if(is != null) {
                is.close();
                is = null;
            }
            if(os != null) {
                os.close();
                os = null;
            }
            if(serialPort != null) {
                serialPort.close();
                serialPort = null;
            }
            isOpen = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface OpenListener {
        void isOpen(boolean opened);
    }

    public interface DataReceiveListener {
        void dataReceive(byte[] data);
    }

    private final class ReceiveThread extends Thread {
        @Override
        public void run() {
            // while (!isInterrupted()), 无法中断？
            while(isOpen){
                try {
                    sleep(100);
                    // Method 1. error, can't return -1
                    /*while((n = is.read(buf, 0, buf.length)) != -1) {
                        bos.write(buf, 0, n);
                    }*/
                    // Method 2. error, is.available() always equal 0.
                    /*if(is.available() > 0) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        byte[] buf = new byte[1024];
                        int n = is.read(buf);
                        bos.write(buf, 0, n);
                        Message msg = Message.obtain(handler, 0, bos.toByteArray());
                        handler.sendMessage(msg);
                    }*/
                    // Method 3. Ok!
                    byte[] buf = new byte[1024];
                    int n;
                    if((n = is.read(buf)) > 0) {    // read is blocked, don't return -1.
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        bos.write(buf, 0, n);
                        Message msg = Message.obtain(handler, 0, bos.toByteArray());
                        handler.sendMessage(msg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private final class SendThread extends Thread {
        private final byte[] data;
        public SendThread(byte[] data, OutputStream outputStream) {
            this.data = data;
        }

        @Override
        public void run() {
            try {
                os.write(data, 0, data.length);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static final class Builder {
        private String path;
        private int baudrate;
        private int flags;
        private OpenListener openListener;
        private DataReceiveListener dataReceiveListener;

        public Builder() {
        }

        public Builder path(String val) {
            path = val;
            return this;
        }

        public Builder baudrate(int val) {
            baudrate = val;
            return this;
        }

        public Builder flags(int val) {
            flags = val;
            return this;
        }

        public Builder openListener(OpenListener val) {
            openListener = val;
            return this;
        }

        public Builder dataReceiveListener(DataReceiveListener val) {
            dataReceiveListener = val;
            return this;
        }

        public SerialPortManager build() {
            return new SerialPortManager(this);
        }
    }
}
