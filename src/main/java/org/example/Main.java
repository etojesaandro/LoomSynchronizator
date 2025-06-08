package org.example;

public class Main {

    public static final int EXECUTION_TIME = 60_000;
    public static final int DEVICE_COUNT = 100_000;

    public static void main(String[] args) {
        DevicePollingServer devicePollingServer = new DevicePollingServer(DEVICE_COUNT);
        devicePollingServer.start();

        waitForFinish();

        devicePollingServer.stop();

        devicePollingServer.printResults();
    }

    private static void waitForFinish() {
        try {
            Thread.sleep(EXECUTION_TIME);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
