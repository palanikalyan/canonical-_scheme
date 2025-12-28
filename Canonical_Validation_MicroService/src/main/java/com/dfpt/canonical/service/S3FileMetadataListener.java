
//             }
//         }
        
//         return null;
//     }
// }


package com.dfpt.canonical.service;

import com.dfpt.canonical.dto.ProcessingResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.awspring.cloud.sqs.annotation.SqsListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.Executor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.UUID;

import org.springframework.stereotype.Service;


@Service
@ConditionalOnProperty(name = "aws.sqs.enabled", havingValue = "true", matchIfMissing = false)
public class S3FileMetadataListener {
    private static final Logger logger = LoggerFactory.getLogger(S3FileMetadataListener.class);

    public S3FileMetadataListener() {
        logger.info("S3FileMetadataListener initialized and ready to listen for SQS messages!");
    }

    @Autowired
    private S3Client s3Client;

    @Autowired
    private TradeProcessingService tradeProcessingService;

    @Autowired
    @Qualifier("s3Executor")
    private Executor s3Executor;

    @Value("${aws.s3.bucket.name:simulator-trade-bucket}")
    private String bucketName;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String extractBucketName(JsonNode messageJson) {
        if (messageJson.has("Records")) {
            JsonNode records = messageJson.get("Records");
            if (records.isArray() && records.size() > 0) {
                JsonNode s3Node = records.get(0).get("s3");
                if (s3Node != null && s3Node.has("bucket")) {
                    JsonNode bucketNode = s3Node.get("bucket");
                    if (bucketNode.has("name")) {
                        return bucketNode.get("name").asText();
                    }
                }
            }
        }
        return null;
    }

    private String extractObjectKey(JsonNode messageJson) {
        if (messageJson.has("Records")) {
            JsonNode records = messageJson.get("Records");
            if (records.isArray() && records.size() > 0) {
                JsonNode s3Node = records.get(0).get("s3");
                if (s3Node != null && s3Node.has("object")) {
                    JsonNode objectNode = s3Node.get("object");
                    if (objectNode.has("key")) {
                        return objectNode.get("key").asText();
                    }
                }
            }
        }
        return null;
    }

   


    @SqsListener("${aws.sqs.queue-name:simulator-trade-queue}")
    public void handleS3FileMetadata(String message) {
        logger.info("Received SQS message: {}", message);
        UUID fileId = UUID.randomUUID();

        // Offload heavy S3 download/processing into s3Executor (single-threaded)
        s3Executor.execute(() -> {
            try {
                logger.info("Processing on thread: {}", Thread.currentThread().getName());
                JsonNode messageJson = objectMapper.readTree(message);

                String bucket = extractBucketName(messageJson);
                String key = extractObjectKey(messageJson);

                if (bucket == null || key == null) {
                    logger.error("Invalid SQS message - missing bucket or key");
                    return;
                }

                logger.info("(async) Processing S3 file: s3://{}/{}", bucket, key);
                String source = "S3";
                logger.info("Generated fileId for S3 file: {}, source: {}", fileId, source);

                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build();

                try (InputStream s3InputStream = s3Client.getObject(getObjectRequest);
                     ReadableByteChannel channel = Channels.newChannel(s3InputStream)) {

                    String fileName = key.substring(key.lastIndexOf('/') + 1);
                    logger.info("Downloaded file from S3: {}", fileName);

                    ProcessingResult result = tradeProcessingService.processTradeFileFromChannel(
                            channel,
                            fileName,
                            fileId,
                            source
                    );

                    logger.info("File processing completed: {} - Status: {} - Success: {}/{}",
                        fileName, result.getStatus(), result.getSuccessCount(), result.getTotalRecords());
                }
            } catch (Exception e) {
                logger.error("Error processing S3 file from SQS message (async)", e);
                // Don't rethrow - task runs async
            }
        });
    }
}