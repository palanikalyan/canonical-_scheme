package com.example.main.core;


public interface OutboxMessagePublisher {
    /**
     * Publish payload to MQ. Throw exception on failure.
     */
    void publish(String payload) throws Exception;
}
