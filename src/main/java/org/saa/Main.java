package org.saa;

import static org.saa.DevicePollingServer.LONG_SYNC_PERIOD_MS;
import static org.saa.DevicePollingServer.SHORT_SYNC_PERIOD_MS;

public class Main {

    public static void main(String[] args) {
        long execTime = Long.parseLong(args[0]);
        long deviceCount = Long.parseLong(args[1]);
        int platformThreads = Integer.parseInt(args[2]);
        boolean virtual = true;
        if (args.length > 3) {
            if (args[3].equals("-l")) {
                virtual = false;
            } else {
                System.exit(1);
            }
        }
        printSystemInfo();
        DevicePollingServer devicePollingServer = new DevicePollingServer(deviceCount, platformThreads, virtual);
        devicePollingServer.start();
        devicePollingServer.waitForFinish(execTime);
        devicePollingServer.stop();
        System.out.printf("Expected variables to by synchronized: %s%n", (long) (deviceCount * execTime * ((1.0 / SHORT_SYNC_PERIOD_MS) + (1.0 / LONG_SYNC_PERIOD_MS))));
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
