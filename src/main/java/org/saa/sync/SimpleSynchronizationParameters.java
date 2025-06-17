package org.saa.sync;

public record SimpleSynchronizationParameters(Long executionTime) implements SynchronizationParameters {

    @Override
    public Long executionTime() {
        return executionTime;
    }
}
