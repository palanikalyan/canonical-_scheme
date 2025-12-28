package com.dfpt.canonical.service;
import com.dfpt.canonical.dto.ExternalTradeDTO;
import com.dfpt.canonical.model.CanonicalTrade;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
@Service
public class FixedWidthParserService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FixedWidthParserService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_FORMATTER_DDMMYYYY = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER_DDMMYYYYHHMMSS = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss");
    public List<ExternalTradeDTO> parseFixedWidthFile(File file) throws IOException {
        List<ExternalTradeDTO> trades = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    logger.debug("Skipping empty line");
                    continue;
                }
                if (line.endsWith("|")) {
                    line = line.substring(0, line.length() - 1);
                }
                logger.info("Processing line length: {} - Line: {}", line.length(), line.substring(0, Math.min(50, line.length())));
                if (line.length() < 130) {
                    logger.warn("Skipping line - too short ({}): {}", line.length(), line.substring(0, Math.min(50, line.length())));
                    continue;
                }
                // Truncate line to first 130 characters for fixed-width parsing (we added schemeId)
                if (line.length() > 130) {
                    line = line.substring(0, 130);
                    logger.debug("Truncated line to 130 characters for parsing");
                }
                ExternalTradeDTO dto = parseLine(line);
                trades.add(dto);
            }
        }
        return trades;
    }
    public CanonicalTrade parseLineToCanonical(String line) {
        CanonicalTrade trade = new CanonicalTrade();
        String originatorTypeStr = extract(line, 1, 1);
        trade.setOriginatorType(originatorTypeStr.isEmpty() ? null : Integer.parseInt(originatorTypeStr));
        trade.setFirmNumber(parseInteger(extract(line, 2, 5)));
        trade.setFundNumber(parseInteger(extract(line, 6, 9)));
        trade.setSchemeId(parseInteger(extract(line, 10, 10)));
        trade.setTransactionType(extract(line, 11, 11));
        trade.setTransactionId(extract(line, 12, 27));
        trade.setTradeDateTime(parseDateTime(extract(line, 28, 41)));
        trade.setDollarAmount(parseAmount(extract(line, 42, 57), 2));
        trade.setClientAccountNo(parseAccountNumber(extract(line, 58, 77)));
        trade.setClientName(extract(line, 78, 97));
        trade.setSsn(extractAlphanumeric(extract(line, 98, 106)));
        trade.setDob(parseDate(extract(line, 107, 114)));
        trade.setShareQuantity(parseAmount(extract(line, 115, 130), 0));
        trade.setStatus("RECEIVED");
        trade.setCreatedAt(LocalDateTime.now());
        return trade;
    }
    public ExternalTradeDTO parseLine(String line) {
        ExternalTradeDTO dto = new ExternalTradeDTO();
        dto.setOriginatorType(extract(line, 1, 1));
        dto.setFirmNumber(parseInteger(extract(line, 2, 5)));
        dto.setFundNumber(parseInteger(extract(line, 6, 9)));
        dto.setSchemeId(parseInteger(extract(line, 10, 10)));
        dto.setTransactionType(extract(line, 11, 11));
        dto.setTransactionId(extract(line, 12, 27));
        dto.setTradeDateTime(extract(line, 28, 41));
        dto.setDollarAmount(parseAmount(extract(line, 42, 57), 2)); 
        dto.setClientAccountNo(parseAccountNumber(extract(line, 58, 77)));
        dto.setClientName(extract(line, 78, 97));
        dto.setSsn(extractAlphanumeric(extract(line, 98, 106)));
        dto.setDob(extract(line, 107, 114));
        dto.setShareQuantity(parseAmount(extract(line, 115, 130), 0));
        return dto;
    }    
    private String extract(String line, int start, int end) {
        int startIdx = start - 1;
        int endIdx = end;
        if (startIdx < 0 || endIdx > line.length()) {
            return "";
        }
        return line.substring(startIdx, endIdx).trim();
    }
    private BigDecimal parseAmount(String value, int decimals) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            long longValue = Long.parseLong(value);
            BigDecimal amount = BigDecimal.valueOf(longValue);
            if (decimals > 0) {
                amount = amount.divide(BigDecimal.TEN.pow(decimals));
            }
            return amount;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(value, DATETIME_FORMATTER_DDMMYYYYHHMMSS);
            } catch (Exception e2) {
                logger.warn("Failed to parse datetime: {}", value);
                return null;
            }
        }
    }
    private LocalDate parseDate(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (Exception e) {
            try {
                return LocalDate.parse(value, DATE_FORMATTER_DDMMYYYY);
            } catch (Exception e2) {
                logger.warn("Failed to parse date: {}", value);
                return null;
            }
        }
    }
    private String extractAlphanumeric(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }
    private Integer parseAccountNumber(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            String numericOnly = value.replaceAll("[^0-9]", "");
            if (numericOnly.isEmpty()) {
                return null;
            }
            return Integer.parseInt(numericOnly);
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse account number: {}", value);
            return null;
        }
    }
}
