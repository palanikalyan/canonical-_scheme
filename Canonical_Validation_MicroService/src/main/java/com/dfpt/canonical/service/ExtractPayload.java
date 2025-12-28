package com.dfpt.canonical.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dfpt.canonical.model.CanonicalTrade;
import com.dfpt.canonical.repository.CanonicalTradeRepository;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class ExtractPayload {

    @Autowired
    private FixedWidthParserService fixedWidthParserService;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private CanonicalTradeRepository tradeRepository;

    @Autowired(required = false)
    private S3FileMetadataListener s3FileMetadataListener; 

    @Autowired
    private OutboxService outboxService;

    @Autowired
    ExceptionOutboxService exceptionOutboxService;

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ExtractPayload.class);
    private final S3Client s3Client = S3Client.builder().build();

    public Map<String, Object> processCentralPubMessage(String inputJson) {

        Map<String, Object> result = new HashMap<>();
        logger.info("Processing central pub message (truncated): {}",
                inputJson != null && inputJson.length() > 200 ? inputJson.substring(0,200) + "..." : inputJson);

        try {
            JsonNode root = mapper.readTree(inputJson);

            UUID rawOrderId = UUID.fromString(root.path("raw_order_id").asText());
            String source = root.path("Source").asText().trim();
            String data = root.path("data").asText();

            result.put("raw_order_id", rawOrderId);
            result.put("source", source);

            // ---------------------- MQ FLOW ---------------------------
            if ("MQ".equalsIgnoreCase(source)) {
                processMQFlow(rawOrderId, data, result);
            }

            // ---------------------- S3 FLOW ---------------------------
            else if ("S3".equalsIgnoreCase(source)) {
                processS3Flow(rawOrderId, data, result);
            }

            logger.info("Processing result: {}", result);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            logger.error("Error processing central pub message", e);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // MQ FLOW
    // -------------------------------------------------------------------------
    private void processMQFlow(UUID rawOrderId, String data, Map<String, Object> result) {

        try {
            result.put("mq_data", data);

            // Parse fixed width
            CanonicalTrade canonical = fixedWidthParserService.parseLineToCanonical(data.trim());
            canonical.setOrderSource("MQ");
            canonical.setRawOrderId(rawOrderId);
            canonical.setCreatedAt(java.time.LocalDateTime.now());

            // Validate
            var validationResult = validationService.validate(canonical);

            if (!validationResult.isValid() || !validationResult.getErrors().isEmpty()) {
                canonical.setStatus("VALIDATION_FAILED");
                canonical.setValidationErrors(String.join("; ", validationResult.getErrors()));
            } else {
                canonical.setStatus("VALIDATED");
            }

            canonical.setValidatedAt(java.time.LocalDateTime.now());

            // DB Save with explicit flush and detailed logging
            CanonicalTrade saved = null;
            try {
                logger.info("Attempting to save canonical trade - txId: {}", canonical.getTransactionId());
                saved = tradeRepository.saveAndFlush(canonical);
                logger.info("Saved canonical trade id: {} txId: {} status: {}", saved.getId(), saved.getTransactionId(), saved.getStatus());

                if ("VALIDATED".equalsIgnoreCase(saved.getStatus())) {
                    try {
                        outboxService.createOutboxEntries(Collections.singletonList(saved));
                        logger.info("Created outbox entry for transaction: {}", saved.getTransactionId());
                    } catch (Exception oe) {
                        logger.error("Failed to create outbox entry for {}", saved.getTransactionId(), oe);
                    }
                } else if ("VALIDATION_FAILED".equalsIgnoreCase(saved.getStatus())) {
                    try {
                        exceptionOutboxService.createExceptionOutboxEntries(Collections.singletonList(saved));
                        logger.info("Created exception outbox entry for FAILED validation transaction: {}", saved.getTransactionId());
                    } catch (Exception oe) {
                        logger.error("Failed to create exception outbox entry for {}", saved.getTransactionId(), oe);
                    }
                }

                result.put("saved_transaction_id", saved.getTransactionId());
                result.put("status", saved.getStatus());
            } catch (org.springframework.dao.DataAccessException dae) {
                logger.error("Database error saving trade txId={}: {}", canonical.getTransactionId(), dae.getMessage(), dae);
                result.put("db_error", dae.getMessage());
            } catch (Exception saveEx) {
                logger.error("Failed to save canonical trade", saveEx);
                result.put("save_error", saveEx.getMessage());
            }

        } catch (Exception ex) {
            logger.error("Error in processMQFlow", ex);
            result.put("mq_error", ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // S3 FLOW
    // -------------------------------------------------------------------------
    private void processS3Flow(UUID rawOrderId, String data, Map<String, Object> result) {
       
           // delegate to S3FileMetadataListener (it will generate its own fileId)
           if (s3FileMetadataListener != null) {
               s3FileMetadataListener.handleS3FileMetadata(data);
               result.put("s3_handled", true);
           } else {
               logger.warn("S3FileMetadataListener not available (aws.sqs.enabled=false) - skipping S3 flow");
               result.put("s3_handled", false);
           }

    }

}
