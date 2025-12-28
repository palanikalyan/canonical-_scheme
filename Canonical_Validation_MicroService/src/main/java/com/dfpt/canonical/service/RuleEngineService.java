package com.dfpt.canonical.service;
import com.dfpt.canonical.model.CanonicalTrade;
import com.dfpt.canonical.dto.ValidationResult;
import com.dfpt.canonical.dto.FundDTO;
import com.dfpt.canonical.dto.ClientDTO;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
@Service
public class RuleEngineService {
    // Primary addError that accepts a code and a descriptive message.
    private boolean addError(ValidationResult res, String code, String error) {
        if (res == null) return false;
        String all = res.getErrors() == null ? "" : res.getErrors().toString();
        if (!all.contains(error)) {
            res.addError(code, error);
            return true;
        }
        return false;
    }

    // Backwards-compatible helper: add error without a code (uses VL000 as unknown code)
    private boolean addError(ValidationResult res, String error) {
        return addError(res, "VL000", error);
    }
    public boolean handleMandatoryFields(CanonicalTrade ct, ValidationResult res) {
        if (ct == null || res == null) return false;
        boolean missing =
                ct.getOriginatorType() == null ||
                ct.getTransactionType() == null || ct.getTransactionType().trim().isEmpty() ||
                ct.getTransactionId() == null || ct.getTransactionId().trim().isEmpty() ||
                ct.getClientName() == null || ct.getClientName().trim().isEmpty() ||
                ct.getFirmNumber() == null ||
                ct.getFundNumber() == null ||
                ct.getClientAccountNo() == null ||
                ct.getTradeDateTime() == null ||
                ct.getDob() == null;
        if (missing) {
            return addError(res, "VL006", "Mandatory fields missing");
        }
        return false;
    }
    // public boolean handleCutoff(CanonicalTrade ct, ValidationResult res, LocalTime navCutoffTime) {
    //     if (ct == null || res == null) return false;
    //     LocalDateTime dt = ct.getTradeDateTime();
    //     LocalTime orderTime = (dt != null) ? dt.toLocalTime() : LocalTime.now();
    //     if (navCutoffTime != null && orderTime.isAfter(navCutoffTime)) {
    //         return addError(res, "Order received after NAV cut-off; will be processed for next day's NAV.");
    //     }
    //     return false;
    // }
    public boolean handleUnknownClient(CanonicalTrade ct, ValidationResult res, ClientDTO client) {
        if (ct == null || res == null) return false;
        if (client == null) {
            return addError(res, "VL012","Unknown client.");
        }
        return false;
    }
    public boolean handleKyc(CanonicalTrade ct, ValidationResult res, ClientDTO client) {
        if (ct == null || res == null) return false;
        String kyc = client == null ? null : client.getKycStatus();
        if (kyc == null || !"YES".equalsIgnoreCase(kyc)) {
            return addError(res, "BR004", "KYC not verified");
        }
        return false;
    }
    public boolean handleUnknownFund(CanonicalTrade ct, ValidationResult res, FundDTO fund) {
        if (ct == null || res == null) return false;
        if (fund == null) {
            return addError(res, "VL002", "Fund doesnt exist");
        }
        return false;
    }
    public boolean handleFundStatus(CanonicalTrade ct, ValidationResult res, FundDTO fund) {
        if (ct == null || res == null) return false;
        String status = fund == null ? null : fund.getStatus();
        if (status == null || !"ACTIVE".equalsIgnoreCase(status)) {
            return addError(res, "BR003", "Scheme not open for transactions");
        }
        return false;
    }
    public boolean handleFundLimits(CanonicalTrade ct, ValidationResult res, FundDTO fund) {
        if (ct == null || res == null) return false;
        if (fund == null) return addError(res, "Unknown scheme/fund."); // defensive
        BigDecimal min = fund.getMinLimit();
        BigDecimal max = fund.getMaxLimit();
        BigDecimal amt = toBigDecimal(ct.getDollarAmount());
        if (amt == null) {
            return addError(res, "VL005", "Amount invalid");
        }
        if (min != null && amt.compareTo(min) < 0) {
            return addError(res, "BR001", "Buy amount below minimum investment");
        }
        if (max != null && amt.compareTo(max) > 0) {
            return addError(res, "BR006", "Trade exceeds maximum limit");
        }
        return false;
    }
    public boolean handleClientStatus(CanonicalTrade ct, ValidationResult res, ClientDTO client) {
        if (ct == null || res == null) return false;
        String status = client == null ? null : client.getStatus();
        if (status == null || !"ACTIVE".equalsIgnoreCase(status)) {
            return addError(res, "BR005", "Account inactive");
        }
        return false;
    }
    public boolean handleBuyOrder(CanonicalTrade ct, ValidationResult res) {
        if (ct == null || res == null) return false;
        boolean changed = false;
        boolean hasAmount = ct.getDollarAmount() != null;
        boolean hasQty = ct.getShareQuantity() != null;
        if (!hasAmount) changed |= addError(res, "VL005", "Amount invalid");
        if (hasQty) changed |= addError(res, "VL010", "Units invalid");
        return changed;
    }
    public boolean handleSellOrder(CanonicalTrade ct, ValidationResult res) {
        if (ct == null || res == null) return false;
        boolean changed = false;
        boolean hasAmount = ct.getDollarAmount() != null;
        boolean hasQty = ct.getShareQuantity() != null;
        if (!hasQty) changed |= addError(res, "VL010", "Units invalid");
        if (hasAmount) changed |= addError(res, "VL005", "Amount invalid");
        return changed;
    }
    public boolean handleSwitchOrder(CanonicalTrade ct, ValidationResult res) {
        if (ct == null || res == null) return false;
        boolean hasAmount = ct.getDollarAmount() != null;
        boolean hasQty = ct.getShareQuantity() != null;
        boolean changed = false;
        if (!hasAmount) changed |= addError(res, "VL005", "Amount invalid");
        if (!hasQty) changed |= addError(res, "VL010", "Units invalid");
        if (changed) return true;
        return false;
    }
    public boolean handleOrderIdInvalid(CanonicalTrade ct, ValidationResult res) {
        if (ct == null || res == null) return false;
        String id = ct.getTransactionId();
        if (id == null) return addError(res, "VL001", "OrderID format invalid");
        String trimmed = id.trim();
        if (trimmed.isEmpty()) return addError(res, "VL001", "OrderID format invalid");
        if (!trimmed.matches("^[A-Za-z0-9_-]{1,100}$")) return addError(res, "VL001", "OrderID format invalid");
        return false;
    }
    public boolean handleTradeDateInvalid(CanonicalTrade ct, ValidationResult res) {
        if (ct == null || res == null) return false;
        java.time.LocalDateTime dt = ct.getTradeDateTime();
        if (dt == null) return addError(res, "VL003", "TradeDate format invalid");
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (dt.isAfter(now)) return addError(res, "VL003", "TradeDate format invalid");
        if (dt.isBefore(now.minusDays(365))) return addError(res, "VL003", "TradeDate format invalid");
        return false;
    }
    public boolean handleTransactionTypeInvalid(CanonicalTrade ct, ValidationResult res) {
        if (ct == null || res == null) return false;
        String t = ct.getTransactionType();
        if (t == null) return addError(res, "VL004", "TransactionType invalid");
        if (!("B".equalsIgnoreCase(t) || "S".equalsIgnoreCase(t) || "E".equalsIgnoreCase(t))) {
            return addError(res, "VL004", "TransactionType invalid");
        }
        return false;
    }
    public boolean handleAmountInvalid(CanonicalTrade ct, ValidationResult res) {
        if (ct == null || res == null) return false;
        java.math.BigDecimal amt = toBigDecimal(ct.getDollarAmount());
        java.math.BigDecimal qty = toBigDecimal(ct.getShareQuantity());
        if (amt == null && qty == null) return addError(res, "VL005", "Amount invalid");
        if (amt != null) {
            if (amt.compareTo(java.math.BigDecimal.ZERO) <= 0) return addError(res, "VL005", "Amount invalid");
            if (amt.scale() > 2) return addError(res, "VL005", "Amount invalid");
        }
        if (qty != null) {
            if (qty.compareTo(java.math.BigDecimal.ZERO) <= 0) return addError(res, "VL010", "Units invalid");
            if (qty.scale() > 4) return addError(res, "VL010", "Units invalid");
        }
        return false;
    }
    public boolean handleAccountNoFormat(CanonicalTrade ct, ValidationResult res) {
        if (ct == null || res == null) return false;
        Integer acc = ct.getClientAccountNo();
        if (acc == null) return addError(res, "VL007", "AccountNo format invalid");
        if (acc <= 0) return addError(res, "VL007", "AccountNo format invalid");
        if (acc > 999999999) return addError(res, "VL007", "AccountNo format invalid");
        return false;
    }
    public boolean handleSsnInvalid(CanonicalTrade ct, ValidationResult res) {
        if (ct == null || res == null) return false;
        String ssn = ct.getSsn();
        if (ssn == null) return addError(res, "VL009", "SSN format invalid");
        String norm = ssn.trim().toUpperCase();
        if (!norm.matches("^[A-Z]{5}[0-9]{4}[A-Z]?$")) return addError(res, "VL009", "SSN format invalid");
        return false;
    }
    public boolean handleFirmNumberInvalid(CanonicalTrade ct, ValidationResult res, com.dfpt.canonical.model.Firm firm) {
        if (ct == null || res == null) return false;
        if (firm == null) return addError(res, "VL011", "Firm number invalid");
        return false;
    }
    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) {
            return BigDecimal.valueOf(((Number) obj).doubleValue());
        }
        try {
            return new BigDecimal(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
