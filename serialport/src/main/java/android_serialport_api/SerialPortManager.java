package android_serialport_api;

public class SerialPortManager {
    private final int baudrate;

    public SerialPortManager(Builder builder) {
        this.baudrate = builder.baudrate;
    }

    public static class Builder {
        private int baudrate;

        public Builder baudrate(int baudrate){
            this.baudrate = baudrate;
            return this;
        }

        public SerialPortManager build(){
            return new SerialPortManager(this);
        }
    }
}
