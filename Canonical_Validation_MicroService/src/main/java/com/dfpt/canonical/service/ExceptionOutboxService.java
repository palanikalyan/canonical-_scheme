package com.dfpt.canonical.service;

import com.dfpt.canonical.model.CanonicalTrade;
import com.dfpt.canonical.model.ExceptionOutboxEntity;
import com.dfpt.canonical.repository.ExceptionOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.dfpt.canonical.dto.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExceptionOutboxService {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionOutboxService.class);

    @Autowired
    private ExceptionOutboxRepository exceptionOutboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional
    public void createExceptionOutboxEntries(List<CanonicalTrade> failedTrades) {
        logger.info("ExceptionOutboxService.createExceptionOutboxEntries() called with {} trades", failedTrades != null ? failedTrades.size() : 0);

        if (failedTrades == null || failedTrades.isEmpty()) {
            logger.debug("No failed trades provided to createExceptionOutboxEntries");
            return;
        }

        // build canonical mapping description -> code
        Map<String, String> descToCode = buildDescToCodeMap();

        List<ExceptionOutboxEntity> list = new ArrayList<>();

        for (CanonicalTrade t : failedTrades) {
            try {
                //String payload = objectMapper.writeValueAsString(t);
                String rawValidation = t.getValidationErrors();
                ObjectNode payloadNode = objectMapper.valueToTree(t);
                String payload ;

                boolean matchedAny = false;
                if (rawValidation != null && !rawValidation.trim().isEmpty()) {
                    String lower = rawValidation.toLowerCase();
                    for (Map.Entry<String, String> me : descToCode.entrySet()) {
                        String desc = me.getKey();
                        if (lower.contains(desc.toLowerCase())) {
                            ExceptionOutboxEntity e = new ExceptionOutboxEntity();
                            e.setErrorCode(me.getValue());
                            e.setErrorReason(desc);

                            payloadNode.put("errorCode", me.getValue());
                            payloadNode.put("errorReason", desc);
                            payload = objectMapper.writeValueAsString(payloadNode);

                            e.setPayload(payload);
                            e.setStatus("PENDING");
                            list.add(e);
                            matchedAny = true;
                        }
                    }
                }

                if (!matchedAny) {
                    ExceptionOutboxEntity e = new ExceptionOutboxEntity();
                    e.setErrorCode("VL000");
                    e.setErrorReason(rawValidation != null && !rawValidation.isEmpty() ? rawValidation : "Validation failed");

                    payloadNode.put("errorCode", "VL000");
                    payloadNode.put("errorReason", rawValidation != null && !rawValidation.isEmpty() ? rawValidation : "Validation failed");
                    payload = objectMapper.writeValueAsString(payloadNode);

                    e.setPayload(payload);
                    e.setStatus("PENDING");
                    list.add(e);
                }

            } catch (Exception ex) {
                logger.error("Failed to serialize failed trade {} for exception outbox", t != null ? t.getId() : null, ex);
            }
        }

        if (!list.isEmpty()) {
            exceptionOutboxRepository.saveAll(list);
            logger.info("Saved {} exception outbox entries", list.size());
        } else {
            logger.warn("No exception entries were created (all failed to serialize)");
        }
    }

    /**
     * Create individual exception outbox entries for a single trade using the ValidationResult.
     * Each error code/message pair will result in one row.
     */
    /*
    @Transactional
    public void createExceptionOutboxEntriesForTrade(CanonicalTrade savedTrade, ValidationResult validationResult) {
        if (savedTrade == null || validationResult == null) return;

        List<ExceptionOutboxEntity> entries = new ArrayList<>();
        String payload;
        try {
            payload = objectMapper.writeValueAsString(savedTrade);
        } catch (Exception e) {
            logger.error("Failed to serialize trade {} for exception outbox", savedTrade.getId(), e);
            return;
        }

        List<String> codes = validationResult.getErrorCodes();
        List<String> errors = validationResult.getErrors();

        // If validationResult contains explicit error codes, use those one-per-row
        if (codes != null && !codes.isEmpty()) {
            for (int i = 0; i < codes.size(); i++) {
                String code = codes.get(i);
                String reason = (errors != null && errors.size() > i) ? errors.get(i) : "Validation error";
                ExceptionOutboxEntity e = new ExceptionOutboxEntity();
                e.setErrorCode(code != null ? code : "VL000");
                e.setErrorReason(reason);
                e.setPayload(payload);
                e.setStatus("PENDING");
                entries.add(e);
            }
        } else if (errors != null && !errors.isEmpty()) {
            // Fallback: try to map textual descriptions to canonical codes
            Map<String, String> descToCode = buildDescToCodeMap();
            for (String err : errors) {
                boolean matched = false;
                if (err != null) {
                    String lower = err.toLowerCase();
                    for (Map.Entry<String, String> me : descToCode.entrySet()) {
                        if (lower.contains(me.getKey().toLowerCase())) {
                            ExceptionOutboxEntity e = new ExceptionOutboxEntity();
                            e.setErrorCode(me.getValue());
                            e.setErrorReason(me.getKey());
                            e.setPayload(payload);
                            e.setStatus("PENDING");
                            entries.add(e);
                            matched = true;
                        }
                    }
                }
                if (!matched) {
                    ExceptionOutboxEntity e = new ExceptionOutboxEntity();
                    e.setErrorCode("VL000");
                    e.setErrorReason(err != null ? err : "Validation error");
                    e.setPayload(payload);
                    e.setStatus("PENDING");
                    entries.add(e);
                }
            }
        }

        if (!entries.isEmpty()) {
            exceptionOutboxRepository.saveAll(entries);
            logger.info("Saved {} exception outbox entries for trade {}", entries.size(), savedTrade.getTransactionId());
        }
    }
    */

    private Map<String, String> buildDescToCodeMap() {
        Map<String, String> descToCode = new HashMap<>();
        descToCode.put("OrderID format invalid", "VL001");
        descToCode.put("Fund doesnt exist", "VL002");
        descToCode.put("TradeDate format invalid", "VL003");
        descToCode.put("TransactionType invalid", "VL004");
        descToCode.put("Amount invalid", "VL005");
        descToCode.put("Mandatory fields missing", "VL006");
        descToCode.put("AccountNo format invalid", "VL007");
        descToCode.put("Duplicate order detected", "VL008");
        descToCode.put("SSN format invalid", "VL009");
        descToCode.put("Units invalid", "VL010");
        descToCode.put("Firm number invalid", "VL011");
        descToCode.put("Buy amount below minimum investment", "BR001");
        descToCode.put("Invalid trade type", "BR002");
        descToCode.put("Scheme not open for transactions", "BR003");
        descToCode.put("KYC not verified", "BR004");
        descToCode.put("Account inactive", "BR005");
        descToCode.put("Trade exceeds maximum limit", "BR006");
        descToCode.put("Insufficient units", "BR007");
        descToCode.put("Account frozen", "BR008");
        return descToCode;
    }
}
