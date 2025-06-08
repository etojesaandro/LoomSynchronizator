package org.example;

public class Main {

    private static final int EXECUTION_TIME = 10_000;
    private static final int DEVICE_COUNT = 5_000;

    public static void main(String[] args) {
        printSystemInfo();

        DevicePollingServer devicePollingServer = new DevicePollingServer(DEVICE_COUNT);
        devicePollingServer.start();

        waitForFinish();

        devicePollingServer.stop();
    }

    private static void waitForFinish() {
        try {
            Thread.sleep(EXECUTION_TIME);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void printSystemInfo() {
        // Runtime information
        Runtime runtime = Runtime.getRuntime();
        System.out.println("===== JVM Information =====");
        System.out.println("JVM Name: " + System.getProperty("java.vm.name"));
        System.out.println("JVM Version: " + System.getProperty("java.vm.version"));
        System.out.println("JVM Vendor: " + System.getProperty("java.vm.vendor"));

        // Memory information
        System.out.println("\n===== Memory/CPU Information =====");
        System.out.printf("Max Memory: %d bytes (%.2f MB)%n",
                runtime.maxMemory(), bytesToMB(runtime.maxMemory()));
        System.out.println("Available Processors: " + runtime.availableProcessors());
    }

    private static double bytesToMB(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }

}
