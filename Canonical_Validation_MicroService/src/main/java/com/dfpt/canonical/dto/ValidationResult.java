package com.dfpt.canonical.dto;
import java.util.ArrayList;
import java.util.List;
public class ValidationResult {
    private boolean valid = true;
    private final List<String> errors = new ArrayList<>();
    private final List<String> errorCodes = new ArrayList<>();
    public boolean isValid() {
        return valid;
    }
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    public List<String> getErrors() {
        return errors;
    }
    public List<String> getErrorCodes() {
        return errorCodes;
    }
    public void addError(String error) {
        this.valid = false;
        this.errors.add(error);
    }
    public void addError(String code, String error) {
        this.valid = false;
        this.errorCodes.add(code);
        this.errors.add(error);
    }
}
