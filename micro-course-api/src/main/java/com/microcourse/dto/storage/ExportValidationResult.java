package com.microcourse.dto.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * 导出校验结果
 */
public class ExportValidationResult {

    private boolean valid;
    private List<String> errors;
    private List<String> warnings;

    public ExportValidationResult() {
        this.valid = true;
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    public static ExportValidationResult success() {
        return new ExportValidationResult();
    }

    public void addError(String error) {
        this.valid = false;
        this.errors.add(error);
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
