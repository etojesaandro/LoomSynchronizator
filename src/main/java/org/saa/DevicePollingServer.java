package org.saa;

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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

import org.saa.sync.SimpleSynchronizationParameters;
import org.saa.sync.Synchronizable;
import org.saa.sync.SynchronizationManager;
import org.saa.sync.SynchronizationParameters;

public class DevicePollingServer {

    public static final long SHORT_SYNC_PERIOD_MS = 500;
    public static final long LONG_SYNC_PERIOD_MS = 2_000;
    public static final long SHORT_EXECUTION_TIME_MS = 10;
    public static final long LONG_EXECUTION_TIME_MS = 500;


    private final ScheduledExecutorService syncPlatformTimersPool = new ScheduledThreadPoolExecutor(
            100,
            Thread.ofPlatform().factory());

    private final ExecutorService syncPlatformThreadsPool = new ThreadPoolExecutor(
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

    private final ScheduledExecutorService syncVirtualTimersPool = Executors.newScheduledThreadPool(0, Thread.ofVirtual().factory());

    private final ExecutorService virtualSyncThreadsPool = Executors.newVirtualThreadPerTaskExecutor();

    private final long deviceCount;
    private final boolean virtual;
    private final List<SynchronizationManager> managers = new ArrayList<>();
    private final LongAdder counter = new LongAdder();
    private long startTime;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private long lastCounter = 0;
    private long lastTimeStamp = System.currentTimeMillis();

    public DevicePollingServer(long deviceCount, boolean virtual) {
        this.deviceCount = deviceCount;
        this.virtual = virtual;
    }

    public void stop() {
        started.set(false);

        System.out.println("Initiate the synchronization stop...");
        List<CompletableFuture<Void>> futures = managers.stream().map(sm -> CompletableFuture.runAsync(sm::stop)).toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (!virtual) {
            syncPlatformThreadsPool.close();
            syncPlatformTimersPool.shutdown();
        } else {
            virtualSyncThreadsPool.close();
            syncVirtualTimersPool.shutdown();
        }

        System.out.printf("Synchronization of %s devices stopped at %s with total execution time: %s ms%n", deviceCount, LocalDateTime.now(), System.currentTimeMillis() - startTime);
        resultsSnapshot();
    }

    public void start() {
        System.out.printf("Initiate the synchronization of %s devices%n", deviceCount);

        ExecutorService syncService = virtualSyncThreadsPool;
        if (!virtual) {
            syncService = syncPlatformThreadsPool;
        }

        ScheduledExecutorService timerService = syncVirtualTimersPool;
        if (!virtual) {
            timerService = syncPlatformTimersPool;
        }

        for (int i = 0; i < deviceCount; i++) {
            SynchronizationManager synchronizationManager = new SynchronizationManager("Device-%s".formatted(i), timerService, syncService, createSynchronizationItem());
            synchronizationManager.scheduleTask(new SimpleSynchronizationParameters(LONG_EXECUTION_TIME_MS), LONG_SYNC_PERIOD_MS);
            managers.add(synchronizationManager);
            synchronizationManager.start(new SimpleSynchronizationParameters(SHORT_EXECUTION_TIME_MS), SHORT_SYNC_PERIOD_MS, 0L);
        }
        startTime = System.currentTimeMillis();
        System.out.printf("Synchronization of %s devices started at %s%n", deviceCount, LocalDateTime.now());
        started.set(true);
    }

    private Synchronizable<SynchronizationParameters> createSynchronizationItem() {
        return new Synchronizable<>() {

            private final ReentrantLock synchronizationLock = new ReentrantLock();

            private String currentMd5;

            @Override
            public ReentrantLock getSynchronizationLock() {
                return synchronizationLock;
            }

            @Override
            public void executeSynchronization(SynchronizationParameters parameters) {
                try {
                    if (!started.get()) {
                        return;
                    }
                    currentMd5 = calculateMD5();
                    Thread.sleep(parameters.executionTime());
                    counter.increment();
                } catch (Exception e) {
                    e.fillInStackTrace();
                }
            }

            private String calculateMD5() throws NoSuchAlgorithmException {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] hashBytes = md.digest("SomeStringToMakeCPUWorks".getBytes());
                return HexFormat.of().formatHex(hashBytes);
            }
        };
    }

    public void resultsSnapshot() {
        System.out.printf("Total Execution Time: %s%n", System.currentTimeMillis() - lastTimeStamp);
        System.out.printf("Actual variables were synchronized: %s%n", counter.sum() - lastCounter);
        lastCounter = counter.sum();
        lastTimeStamp = System.currentTimeMillis();
    }

    public void waitForFinish(long executionTime) {
        try {
            Thread.sleep(executionTime);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}