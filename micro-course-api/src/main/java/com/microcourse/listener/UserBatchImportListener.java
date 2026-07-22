package com.microcourse.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.microcourse.dto.BatchImportResultVO.ImportErrorItem;
import com.microcourse.dto.UserBatchImportDTO;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 用户批量导入 Excel 解析监听器
 * P1-2 修复：移除硬编码弱密码，生成随机 8 位字母+数字密码
 */
public class UserBatchImportListener implements ReadListener<UserBatchImportDTO> {

    private final List<UserBatchImportDTO> rows = new ArrayList<>();
    private final List<ImportErrorItem> errors = new ArrayList<>();

    /** 密码复杂度: 至少 8 位，含字母和数字 */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public UserBatchImportListener() {
    }

    @Override
    public void invoke(UserBatchImportDTO data, AnalysisContext context) {
        int rowIndex = context.readRowHolder().getRowIndex() + 1;

        // 校验必填字段
        if (data.getUsername() == null || data.getUsername().trim().isEmpty()) {
            errors.add(new ImportErrorItem(rowIndex, "", "用户名不能为空"));
            return;
        }
        String username = data.getUsername().trim();
        data.setUsername(username);

        if (data.getRealName() == null || data.getRealName().trim().isEmpty()) {
            errors.add(new ImportErrorItem(rowIndex, username, "真实姓名不能为空"));
            return;
        }
        data.setRealName(data.getRealName().trim());

        // 处理密码：有则校验复杂度，无则生成随机密码
        if (data.getPassword() != null && !data.getPassword().trim().isEmpty()) {
            if (!PASSWORD_PATTERN.matcher(data.getPassword().trim()).matches()) {
                errors.add(new ImportErrorItem(rowIndex, username,
                        "密码需至少 8 位且包含字母和数字"));
                return;
            }
        } else {
            data.setPassword(generateRandomPassword());
        }

        rows.add(data);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 解析完成后不做自动保存，由调用方控制事务
    }

    /**
     * 生成随机 8 位密码，保证至少含 1 个字母 + 1 个数字
     */
    public static String generateRandomPassword() {
        char[] pwd = new char[8];
        // 第一位保证字母
        pwd[0] = PASSWORD_CHARS.charAt(RANDOM.nextInt(48)); // A-z range
        // 第二位保证数字
        pwd[1] = PASSWORD_CHARS.charAt(48 + RANDOM.nextInt(8)); // 2-9 range
        for (int i = 2; i < 8; i++) {
            pwd[i] = PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length()));
        }
        // Fisher-Yates shuffle
        for (int i = 7; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = pwd[i];
            pwd[i] = pwd[j];
            pwd[j] = tmp;
        }
        return new String(pwd);
    }

    public List<UserBatchImportDTO> getRows() {
        return rows;
    }

    public List<ImportErrorItem> getErrors() {
        return errors;
    }
}
