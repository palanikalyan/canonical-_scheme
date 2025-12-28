package com.dfpt.canonical.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
@Entity
@BatchSize(size = 100)
@Table(name = "outbox")
public class OutboxEntity {
    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID outboxId; // ID of each row (record) in outbox

    @NotNull(message = "Trade record ID can't be null!")
    @Column(name = "aggregate_id", unique = true)
    private UUID aggregateId; // ID of each trade order
    
    @NotNull(message = "Trade record date can't be null!")
    @Column(name = "trade_datetime")
    private LocalDateTime tradeDateTime; // Trade date time to cut cut-off time

    @NotNull(message = "Payload can't be null!")
    @Column(name = "payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;  // JSON string trade order payload

    @NotNull(message = "Status in outbox can't be null!")
    @Column(name = "status", length = 20)
    private String status;  // Status of each order is outbox like NEW, PENDING, FAILED

    @Column(name = "redis_message_id", length = 64)
    private String redisMessageId; // Redis stream entry id (serves as ACK)

    @Column(name = "ack_received_at")
    private LocalDateTime ackReceivedAt; // Timestamp when Redis ID was received

    @NotNull(message = "Created time of order request in outbox can't be null!")
    @Column(name = "created_at")
    private LocalDateTime createdAt; // Time at which order was placed in outbox
    
    @NotNull(message = "Processing date of order can't be null!")
    @Column(name = "processing_date")
    private LocalDate processingDate; // Based on cut-off time, date is today or today + 1

    @Column(name = "last_attempt_at") // Can be null when no failure on send occurs
    private LocalDateTime lastAttemptAt; // Last attempted retry time of order when sending it fails

    @NotNull(message = "Retry count of order in outbox can't be null!")
    @Column(name = "retry_count", columnDefinition = "integer default 0")
    private Integer retryCount;

    // Additional columns required by central-event-publisher
    @Column(name = "raw_order_id")
    private UUID rawOrderId;

    @Column(name = "source", length = 10)
    private String source; // MQ or S3

    public UUID getOutboxId() {
        return outboxId;
    }

    public void setOutboxId(UUID outboxId) {
        this.outboxId = outboxId;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public LocalDateTime getTradeDateTime() {
        return tradeDateTime;
    }

    public void setTradeDateTime(LocalDateTime tradeDateTime) {
        this.tradeDateTime = tradeDateTime;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRedisMessageId() {
        return redisMessageId;
    }

    public void setRedisMessageId(String redisMessageId) {
        this.redisMessageId = redisMessageId;
    }

    public LocalDateTime getAckReceivedAt() {
        return ackReceivedAt;
    }

    public void setAckReceivedAt(LocalDateTime ackReceivedAt) {
        this.ackReceivedAt = ackReceivedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(LocalDateTime lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public UUID getRawOrderId() {
        return rawOrderId;
    }

    public void setRawOrderId(UUID rawOrderId) {
        this.rawOrderId = rawOrderId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
    
    public LocalDate getProcessingDate() {
        return processingDate;
    }

    public void setProcessingDate(LocalDate processingDate) {
        this.processingDate = processingDate;
    }
}
