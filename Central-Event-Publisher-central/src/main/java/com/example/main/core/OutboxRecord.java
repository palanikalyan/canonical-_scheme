package com.example.main.core;
import java.util.UUID;
public class OutboxRecord {
    private UUID id;  // Changed to Object to support both Long (JDBC) and ObjectId (MongoDB)
    private String payload;
    // you can add extra columns as needed

    public OutboxRecord() {}

    public OutboxRecord(UUID id, String payload) {
        this.id = id;
        this.payload = payload;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
