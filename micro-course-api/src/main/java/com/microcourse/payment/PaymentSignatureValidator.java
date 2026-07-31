package com.microcourse.payment;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 支付回调 HMAC-SHA256 签名验证器（从 OrderPaymentServiceImpl 提取）。
 *
 * <h3>背景</h3>
 * <p>R3 拆分 OrderServiceImpl 时，原 paymentCallback 内联了 HMAC 验证 + 计算逻辑，
 * 加上导入常量约 80 行。为保持 OrderPaymentServiceImpl < 400 行（R1 precheck 要求），
 * 提取为独立 Spring 组件。</p>
 *
 * <h3>签名算法</h3>
 * <pre>rawBody = method=KEY&orderNo=KEY&...（按 key 排序，排除 sign 字段）
 * sign    = Base64(HmacSHA256(rawBody, secret))</pre>
 *
 * <h3>环境行为</h3>
 * <ul>
 *   <li><b>secret 空 + 生产 profile</b>：拒绝（抛 503），强制要求配置密钥</li>
 *   <li><b>secret 空 + 非生产</b>：放行 + warn（dev mock 模式兼容）</li>
 *   <li><b>secret 非空</b>：强制校验 X-Signature header，缺失或不一致 → 抛 1001</li>
 * </ul>
 *
 * <h3>生产安全</h3>
 * <p>生产环境必须通过环境变量 {@code PAYMENT_CALLBACK_SECRET} 注入足够强度的 secret。
 * 留空会被拒绝，符合 SEC-004 安全规范。</p>
 */
@Component
public class PaymentSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(PaymentSignatureValidator.class);

    @Value("${payment.callback-secret:}")
    private String secret;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    /**
     * 校验支付回调签名。失败时抛 BusinessException（与全局异常处理器统一返回 401 + 业务码 1001）。
     *
     * @param params 回调参数（含 sign 字段，验证后会被排除）
     * @throws BusinessException 1001 - 签名缺失/不匹配/异常
     * @throws BusinessException 1008 - 生产环境 secret 未配置
     */
    public void validate(Map<String, String> params) {
        if (secret == null || secret.isBlank()) {
            boolean isProduction = activeProfiles != null && activeProfiles.contains("prod");
            if (isProduction) {
                log.error("[paymentCallback] 生产环境 payment.callback-secret 未配置，拒绝支付回调");
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "支付回调密钥未配置");
            }
            log.warn("[paymentCallback] ⚠️ 开发环境无回调密钥，mock 模式下允许");
            return;
        }

        String receivedSign = params.get("sign");
        if (receivedSign == null || receivedSign.isBlank()) {
            log.warn("[paymentCallback] 缺少签名，拒绝回调: params={}",
                    LogSanitizer.sanitizeForLog(params.toString(), 500));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "缺少支付回调签名");
        }

        String computedSign = computeHmac(params);
        if (!computedSign.equals(receivedSign)) {
            log.warn("[paymentCallback] 签名验证失败: received={}, computed={}", receivedSign, computedSign);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "支付回调签名验证失败");
        }
        log.info("[paymentCallback] 签名验证通过");
    }

    /**
     * 计算 HMAC-SHA256 签名（按 key 排序，排除 sign 字段）。
     * 公开供 OrderController 自身复用（直接在 rawBody 模式）。
     */
    public String computeHmac(Map<String, String> params) {
        try {
            StringBuilder sb = new StringBuilder();
            params.entrySet().stream()
                    .filter(e -> !"sign".equals(e.getKey()))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sb.append(e.getKey()).append("=").append(e.getValue()).append("&"));
            if (sb.length() > 0) sb.setLength(sb.length() - 1);
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("[paymentCallback] HMAC 签名计算异常", e);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "签名验证异常");
        }
    }
}
