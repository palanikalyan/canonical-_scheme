package com.dfpt.canonical.controller;

import com.dfpt.canonical.dto.ValidationResult;
import com.dfpt.canonical.model.CanonicalTrade;
import com.dfpt.canonical.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/validation")
@Tag(name = "Validation API", description = "Endpoints for validating canonical trades and health check.")
public class RuleEngineController {
    
    private static final Logger logger = LoggerFactory.getLogger(RuleEngineController.class);
    
    @Autowired
    private ValidationService validationService;
    
    /**
     * Endpoint to validate a canonical trade
     * 
     * @param data The canonical trade data to validate
     * @return ValidationResult containing validation status and any errors
     */
    @Operation(summary = "Validate a canonical trade", description = "Validates the provided canonical trade data and returns the validation result.")
    @PostMapping("/validate")
    public ResponseEntity<ValidationResult> validate(@RequestBody CanonicalTrade data) {
        logger.info("Received validation request for trade: {}", data.getTransactionId());
        try {
            ValidationResult result = validationService.validate(data);
            logger.info("Validation completed with status: {}", (result.getErrors().isEmpty() ? "PASSED" : "FAILED"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error processing validation request", e);
            ValidationResult errorResult = new ValidationResult();
            errorResult.addError("Controller error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }
    
    /**
     * Health check endpoint for the rule engine service
     * 
     * @return Simple status message
     */
    @Operation(summary = "Health check", description = "Returns the running status of the Rule Engine Service.")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Rule Engine Service is running");
    }
}