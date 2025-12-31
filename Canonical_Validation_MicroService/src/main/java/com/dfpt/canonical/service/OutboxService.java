package com.dfpt.canonical.service;

import com.dfpt.canonical.model.CanonicalTrade;
import com.dfpt.canonical.model.OutboxEntity;
import com.dfpt.canonical.model.OutboxStatus;
import com.dfpt.canonical.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {

    private static final Logger logger = LoggerFactory.getLogger(OutboxService.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String REDIS_STREAM_KEY = "trade-events-stream";
    
    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Value("${outbox.queue-name:file-upload-queue}")
    private String outboxQueueName;
    
    @Value("${outbox.local.publish.enabled:false}")
    private boolean localPublishEnabled;

    private static final LocalTime CUT_OFF_TIME = LocalTime.of(15, 0); // 3:00 PM

    // Method to check if order has come after cut-off time
    public static boolean isAfter3PM(LocalDateTime inputDateTime) {
        return inputDateTime.toLocalTime().isAfter(CUT_OFF_TIME);
    }

    // Creating outbox entries for validated records that are saved
    public void createOutboxEntries(List<CanonicalTrade> savedOrders) {
        logger.info("OutboxService.createOutboxEntries() called with {} trades", savedOrders != null ? savedOrders.size() : 0);
        
        if (savedOrders == null || savedOrders.isEmpty()) {
            logger.warn("No trades provided to createOutboxEntries");
            return;
        }
        
        for (CanonicalTrade saved : savedOrders) {
            try {
            	createOutboxRecord(saved);

            } catch (Exception e) {
                logger.error("Failed to save order {} to outbox!", saved.getId(), e);
                throw new RuntimeException("JSON conversion failed", e);
            }
        }
    }
    
    // Creating outbox entries one by one
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createOutboxRecord(CanonicalTrade order) {
        String payloadJson = null;
        try {
            // Serialize order but strip sensitive/outbox-unwanted fields (schemeId/schemeCode) to avoid DB constraints
            Map<String, Object> payloadMap = objectMapper.convertValue(order, Map.class);
            
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            logger.error("JSON conversion failed for trade ID: {}", order.getId(), e);
            throw new RuntimeException("JSON conversion failed", e);
        }

        OutboxEntity outbox = new OutboxEntity();
        UUID rawOrderId = order.getRawOrderId() != null ? order.getRawOrderId() : order.getId();
        outbox.setRawOrderId(rawOrderId);
        
        String source = order.getOrderSource();
        UUID aggregateId;
        if ("MQ".equalsIgnoreCase(order.getOrderSource())) {
            aggregateId = order.getRawOrderId() != null ? order.getRawOrderId() : order.getId();
            rawOrderId = order.getRawOrderId();
            logger.debug("MQ order - using rawOrderId: {}", aggregateId);
        } else if ("S3".equalsIgnoreCase(order.getOrderSource())) {
            aggregateId = order.getFileId() != null ? order.getFileId() : order.getId();
            rawOrderId = order.getId();
            logger.debug("S3 order - using fileId: {}", aggregateId);
        } else {
            aggregateId = order.getId();
            rawOrderId = order.getId();
            logger.debug("Unknown source - using canonical ID: {}", aggregateId);
        }

        outbox.setAggregateId(aggregateId);
        outbox.setRawOrderId(rawOrderId);
        outbox.setSource(source);
        outbox.setPayload(payloadJson);
        
        LocalDate tradeDate = order.getTradeDateTime().toLocalDate();

        if (isAfter3PM(order.getTradeDateTime())) {
            outbox.setStatus(OutboxStatus.LATE.name());
            outbox.setProcessingDate(tradeDate.plusDays(1)); // Will be processed tomorrow
        } else {
            outbox.setStatus(OutboxStatus.NEW.name());
            outbox.setProcessingDate(tradeDate); // Will be processed today
        }
        
        outbox.setTradeDateTime(order.getTradeDateTime());
        outbox.setCreatedAt(LocalDateTime.now());
        outbox.setRetryCount(0);

        outboxRepository.save(outbox);
    }

    // Process NEW events in outbox
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processNewEvents() {
        try {
            List<OutboxEntity> newEvents = outboxRepository.findByStatus("NEW");

            if (!newEvents.isEmpty()) {
                logger.info("Processing {} new outbox events", newEvents.size());

                for (OutboxEntity event : newEvents) {
                    try {
                        RecordId recordId = sendToRedisStream(event);

                        event.setRedisMessageId(recordId != null ? recordId.getValue() : null);
                        event.setLastAttemptAt(LocalDateTime.now());

                        // If local publish is enabled, send to JMS and mark as SENT locally.
                        // Otherwise, mark as PENDING so Central Event Publisher will pick it up and publish.
                        if (localPublishEnabled) {
                            try {
                                jmsTemplate.convertAndSend(outboxQueueName, event.getPayload());
                                event.setAckReceivedAt(LocalDateTime.now());
                                event.setStatus(OutboxStatus.SENT.name());
                                outboxRepository.save(event);
                                logger.debug("Successfully sent event {} to Redis stream id {} and JMS queue {}", event.getOutboxId(), recordId, outboxQueueName);
                            } catch (Exception jmsEx) {
                                logger.error("Failed to send event {} to JMS queue {}", event.getOutboxId(), outboxQueueName, jmsEx);
                                event.setStatus(OutboxStatus.FAILED.name());
                                event.setRetryCount(event.getRetryCount() + 1);
                                event.setLastAttemptAt(LocalDateTime.now());
                                outboxRepository.save(event);
                            }
                        } else {
                            event.setStatus(OutboxStatus.PENDING.name());
                            outboxRepository.save(event);
                            logger.debug("Successfully sent event {} to Redis stream id {} and marked as PENDING for central processing", event.getOutboxId(), recordId);
                        }

                    } catch (Exception e) {
                        logger.error("Failed to send event {} to Redis stream", event.getOutboxId(), e);

                        event.setStatus(OutboxStatus.FAILED.name());
                        event.setRetryCount(event.getRetryCount() + 1);
                        event.setLastAttemptAt(LocalDateTime.now());
                        outboxRepository.save(event);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error processing new outbox events", e);
        }
    }

    // Retry failed outbox events
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void retryFailedEvents() {
        try {
            List<OutboxEntity> retryableEvents = outboxRepository.findRetryableEvents(MAX_RETRY_ATTEMPTS);

            if (!retryableEvents.isEmpty()) {
                logger.info("Retrying {} failed outbox events", retryableEvents.size());

                for (OutboxEntity event : retryableEvents) {
                    try {
                        RecordId recordId = sendToRedisStream(event);

                        event.setRedisMessageId(recordId != null ? recordId.getValue() : null);
                        event.setLastAttemptAt(LocalDateTime.now());

                        if (localPublishEnabled) {
                            try {
                                jmsTemplate.convertAndSend(outboxQueueName, event.getPayload());
                                event.setAckReceivedAt(LocalDateTime.now());
                                event.setStatus(OutboxStatus.SENT.name());
                                outboxRepository.save(event);
                                logger.info("Successfully retried event {} after {} attempts, redisId={}, jmsQueue={}",
                                           event.getOutboxId(), event.getRetryCount(), recordId, outboxQueueName);
                            } catch (Exception jmsEx) {
                                logger.error("Failed to send retried event {} to JMS queue {}", event.getOutboxId(), outboxQueueName, jmsEx);
                                event.setRetryCount(event.getRetryCount() + 1);
                                event.setLastAttemptAt(LocalDateTime.now());
                                if (event.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
                                    logger.error("Max retry attempts exceeded for event {}", event.getOutboxId());
                                }
                                outboxRepository.save(event);
                            }
                        } else {
                            // Mark back to PENDING and save so central picks it up again
                            event.setStatus(OutboxStatus.PENDING.name());
                            outboxRepository.save(event);
                            logger.info("Retry sent event {} to Redis, re-marked as PENDING for central processing", event.getOutboxId());
                        }

                    } catch (Exception e) {
                        logger.error("Retry failed for event {} (attempt {})",
                                   event.getOutboxId(), event.getRetryCount() + 1, e);

                        event.setRetryCount(event.getRetryCount() + 1);
                        event.setLastAttemptAt(LocalDateTime.now());

                        if (event.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
                            logger.error("Max retry attempts exceeded for event {}", event.getOutboxId());
                        }

                        outboxRepository.save(event);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error retrying failed outbox events", e);
        }
    }

    // Send outbox event to Redis Stream
    private RecordId sendToRedisStream(OutboxEntity event) {
        try {
            StreamOperations<String, Object, Object> streamOps = redisTemplate.opsForStream();
            
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("aggregateId", event.getAggregateId().toString());
            eventData.put("payload", event.getPayload());
            eventData.put("tradeDateTime", event.getTradeDateTime().toString());
            eventData.put("outboxId", event.getOutboxId().toString());
            
            RecordId recordId = streamOps.add(REDIS_STREAM_KEY, eventData);
            
            logger.debug("Event {} sent to Redis stream {} with id {}", event.getOutboxId(), REDIS_STREAM_KEY, recordId);
            return recordId;
        } catch (Exception e) {
            logger.error("Failed to send event {} to Redis stream", event.getOutboxId(), e);
            throw e; // Re-throw to trigger retry logic
        }
    }
    
    // Change state from LATE to NEW for yesterday's late orders
    @Scheduled(cron = "0 */10 15-17 * * *")
    public void lateOrdersStateChange() {
    	LocalDate today = LocalDate.now();
    	
    	List<OutboxEntity> lateOrders = outboxRepository.findLateEventsByProcessingDate(today);
    	
    	if (lateOrders.isEmpty()) {
    		logger.info("No LATE orders found in outbox..");
    		return;
    	}
    	
    	logger.info("Found {} LATE orders in outbox. Processing them..", lateOrders.size());
    	
    	activateLateOrders(lateOrders);

    }
    
    // Change status from LATE to NEW (used by scheduler that checks LATE orders)
    @Transactional
    public void activateLateOrders(List<OutboxEntity> lateOrders) {
    	for (OutboxEntity order : lateOrders) {
            order.setStatus(OutboxStatus.NEW.name());
        }
    	
    	outboxRepository.saveAll(lateOrders);
    }

}