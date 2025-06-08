package org.example;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

import org.example.sync.SimpleSynchronizationParameters;
import org.example.sync.Synchronizable;
import org.example.sync.SynchronizationManager;
import org.example.sync.SynchronizationParameters;

public class DevicePollingServer {

    public static final int EXECUTION_TIME = 60_000;
    public static final int DEVICE_COUNT = 100_000;
    public static final long SHORT_SYNC_PERIOD_MS = 500;
    public static final long LONG_SYNC_PERIOD_MS = 2_000;
    public static final long SHORT_EXECUTION_TIME_MS = 10;
    public static final long LONG_EXECUTION_TIME_MS = 500;

    private final ScheduledThreadPoolExecutor syncTimersPool = new ScheduledThreadPoolExecutor(
            100,
            Thread.ofPlatform().factory());

    private final ThreadPoolExecutor syncThreadsPool = new ThreadPoolExecutor(
            10000,
            10000,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100_000),
            runnable -> {
                Thread thread = Thread.ofPlatform().factory().newThread(runnable);
                thread.setName("Synchronizer");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    private final ExecutorService virtualSyncThreadsPool = Executors.newVirtualThreadPerTaskExecutor();

    private final int deviceCount;
    private final List<SynchronizationManager> managers = new ArrayList<>();
    private final LongAdder counter = new LongAdder();
    private long startTime;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private long totalTime = 0;

    public DevicePollingServer(int deviceCount) {
        this.deviceCount = deviceCount;
    }


    public void stop() {
        started.set(false);

        System.out.println("Initiate the synchronization stop...");
        List<CompletableFuture<Void>> futures = managers.stream().map(sm -> CompletableFuture.runAsync(sm::stop)).toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        syncThreadsPool.shutdown();

        syncTimersPool.shutdown();

        totalTime = System.currentTimeMillis() - startTime;

        System.out.printf("Synchronization of %s devices stopped at %s with total execution time: %sms%n", deviceCount, LocalDateTime.now(), System.currentTimeMillis() - startTime);
    }

    public void start() {
        started.set(true);
        System.out.printf("Initiate the synchronization of %s devices%n", deviceCount);
        for (int i = 0; i < deviceCount; i++) {
            SynchronizationManager synchronizationManager = new SynchronizationManager("Device-%s".formatted(i), syncTimersPool, syncThreadsPool, createSynchronizationItem());
            synchronizationManager.scheduleTask(new SimpleSynchronizationParameters(LONG_EXECUTION_TIME_MS), LONG_SYNC_PERIOD_MS);
            managers.add(synchronizationManager);
            synchronizationManager.start(new SimpleSynchronizationParameters(SHORT_EXECUTION_TIME_MS), SHORT_SYNC_PERIOD_MS, 0L);
        }
        startTime = System.currentTimeMillis();
        System.out.printf("Synchronization of %s devices started at %s%n", deviceCount, LocalDateTime.now());
    }

    private Synchronizable<SynchronizationParameters> createSynchronizationItem() {
        return new Synchronizable<>() {

            private final ReentrantLock synchronizationLock = new ReentrantLock();

            @Override
            public ReentrantLock getSynchronizationLock() {
                return synchronizationLock;
            }

            @Override
            public void executeSynchronization(SynchronizationParameters parameters) {
                try {
                    if (!started.get())
                    {
                        return;
                    }
                    calculateMD5("SomeStringToMakeCPUWorks");
                    Thread.sleep(parameters.executionTime());
                    counter.increment();
                } catch (Exception e) {
                    e.fillInStackTrace();
                }
            }

            private String calculateMD5(String input) throws NoSuchAlgorithmException {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] hashBytes = md.digest(input.getBytes());
                return HexFormat.of().formatHex(hashBytes);
            }

        };
    }

    public void printResults() {
        System.out.printf("%s variables were synchronized in %s seconds", counter.sum(), totalTime);
    }
}