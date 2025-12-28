package com.dfpt.canonical.service;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.github.sushmithashiva04ops.centraleventpublisher.listener.DynamicOutboxListener;



@Service
public class CentralPubListener {

    private final DynamicOutboxListener outboxListener; 
    private final ExtractPayload extractPayload; 

    private int latestSize = 0;       // queue size from scheduler
    private int currentIndex = 0;     // next message to parse

    public CentralPubListener(DynamicOutboxListener outboxListener, ExtractPayload extractPayload) {
        this.outboxListener = outboxListener;
        this.extractPayload = extractPayload;
    }

    @Scheduled(fixedRate = 5000)
    public void processMessagesScheduled() {
        processMessages();
    }

    // This is your parser method (you call this repeatedly)
    public void processMessages() {

        // get full list of messages (old + new)
        List<String> allMessages = outboxListener.getMessages("outbox.event");

        // compute latest size dynamically so new messages are picked up immediately
        int latest = allMessages.size();

        // if queue has no new data, stop
        if (currentIndex >= latest) {
            System.out.println("No new messages to parse.");
            return;
        }

        // parse from currentIndex to latest
        List<String> toParse = allMessages.subList(currentIndex, latest);

        System.out.println("Parsing messages from index " + currentIndex + " to " + (latest - 1));

        sendMessage(toParse);

        // move pointer forward
        currentIndex = latest;
    }

    public void sendMessage(List<String> messages) {
        for (String msg : messages) {
            try {
                System.out.println("Extracted payload: " + msg);
                var res = extractPayload.processCentralPubMessage(msg);  // your parsing logic
                if (res.containsKey("save_error")) {
                    System.err.println("Failed to persist payload: " + res.get("save_error"));
                } else if (res.containsKey("saved_transaction_id")) {
                    System.out.println("Successfully processed and saved transaction: " + res.get("saved_transaction_id"));
                } else {
                    System.out.println("Processed payload but no DB save/action was recorded: " + res);
                }
            } catch (Exception e) {
                System.err.println("Error processing extracted payload: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
