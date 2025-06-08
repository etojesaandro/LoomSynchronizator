package org.example;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class DummyServer implements Server {

    public static final int TIMEOUT = 10;

    public final AtomicInteger counter = new AtomicInteger(0);
    public int previousCounter = 0;

    private final ExecutorService executorService;
    private volatile boolean started = false;

    public DummyServer(ExecutorService executorService) {
        this.executorService = executorService;
        started = true;
    }

    @Override
    public Future<String> request(String input) {
        return executorService.submit(() -> {
            try {
                String hash = generatePasswordMD5Hash(input);
                sendMessageToBabushka(hash);
                //counter.incrementAndGet();
                return hash;
            } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void sendMessageToBabushka(String hash) {
        try {
            Thread.sleep(TIMEOUT);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private String generatePasswordMD5Hash(String input) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return Math.sin(input.length()) + "";
        /*MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(input.getBytes(UTF_8));
        byte[] byteData = md.digest();
        return HexFormat.of().formatHex(byteData);*/
    }

    public void startAsyncChecker() {
        new Thread(() -> {
            while (started) {
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                System.out.printf("Current hash rate: %s hashes/sec%n", counter.get() - previousCounter);
                previousCounter = counter.get();
            }
        }).start();
    }

    public void stop() {
        started = false;
    }
}
