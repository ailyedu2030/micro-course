package com.microcourse.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.microcourse.dto.UserBatchImportDTO;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 用户批量导入 Excel 解析监听器
 */
public class UserBatchImportListener implements ReadListener<UserBatchImportDTO> {

    private final List<UserBatchImportDTO> rows = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final BCryptPasswordEncoder passwordEncoder;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final String DEFAULT_PASSWORD = "123456";

    public UserBatchImportListener(BCryptPasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void invoke(UserBatchImportDTO data, AnalysisContext context) {
        int rowIndex = context.readRowHolder().getRowIndex() + 1;

        // 校验必填字段
        if (data.getUsername() == null || data.getUsername().trim().isEmpty()) {
            errors.add("第 " + rowIndex + " 行：用户名不能为空");
            return;
        }
        if (data.getRealName() == null || data.getRealName().trim().isEmpty()) {
            errors.add("第 " + rowIndex + " 行：真实姓名不能为空");
            return;
        }

        // 校验邮箱格式
        if (data.getEmail() != null && !data.getEmail().trim().isEmpty()) {
            if (!EMAIL_PATTERN.matcher(data.getEmail()).matches()) {
                errors.add("第 " + rowIndex + " 行：邮箱格式不正确");
                return;
            }
        }

        rows.add(data);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 解析完成后不做自动保存，由调用方控制事务
    }

    public List<UserBatchImportDTO> getRows() {
        return rows;
    }

    public List<String> getErrors() {
        return errors;
    }

    public String getDefaultPassword() {
        return DEFAULT_PASSWORD;
    }

    public BCryptPasswordEncoder getPasswordEncoder() {
        return passwordEncoder;
    }
}