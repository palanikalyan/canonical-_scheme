package com.dfpt.canonical.model;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "ms_exception_outbox")
public class ExceptionOutboxEntity {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    /*
    @NotNull
    @Column(name = "error_code", length = 20, nullable = false)
    private String errorCode;

    @NotNull
    @Column(name = "error_reason", columnDefinition = "text", nullable = false)
    private String errorReason;
    */

    @NotNull
    @Column(name = "payload", columnDefinition = "text", nullable = false)
    private String payload;

    @Column(name = "status", length = 20)
    private String status = "PENDING";

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @NotNull
    @Column(name = "error_code", length = 20, nullable = false)
    private String errorCode;

    @NotNull
    @Column(name = "error_reason", columnDefinition = "text", nullable = false)
    private String errorReason;

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public void setErrorReason(String errorReason) {
        this.errorReason = errorReason;
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
}
