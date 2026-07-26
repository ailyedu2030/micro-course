package com.microcourse.service.impl;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.AdminSettingService;
import com.microcourse.service.CasTicketValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.xml.sax.InputSource;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * CAS 票据校验服务。
 */
@Service
public class CasTicketValidationServiceImpl implements CasTicketValidationService {

    private static final Logger log = LoggerFactory.getLogger(CasTicketValidationServiceImpl.class);

    private final AdminSettingService adminSettingService;
    private final RestTemplate restTemplate;

    public CasTicketValidationServiceImpl(AdminSettingService adminSettingService,
                                          @Qualifier("interactiveRestTemplate") RestTemplate restTemplate) {
        this.adminSettingService = adminSettingService;
        this.restTemplate = restTemplate;
    }

    @Override
    public String validateTicket(String ticket) {
        String casServerUrl = adminSettingService.getByKey("cas_server_url");
        String casServiceUrl = adminSettingService.getByKey("cas_service_url");

        if (casServerUrl == null || casServerUrl.isBlank()) {
            throw new BusinessException(ErrorCode.CAS_NOT_CONFIGURED, "CAS服务地址未配置，请在系统设置中配置 cas_server_url");
        }
        if (casServiceUrl == null || casServiceUrl.isBlank()) {
            throw new BusinessException(ErrorCode.CAS_NOT_CONFIGURED, "CAS回调地址未配置，请在系统设置中配置 cas_service_url");
        }

        String encodedServiceUrl = URLEncoder.encode(casServiceUrl, StandardCharsets.UTF_8);
        String baseUrl = casServerUrl.endsWith("/") ? casServerUrl.substring(0, casServerUrl.length() - 1) : casServerUrl;
        String validateUrl = baseUrl + "/serviceValidate?ticket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8)
                + "&service=" + encodedServiceUrl;

        log.info("[CAS] 验证票据 validateUrl={}", validateUrl);

        String xmlResponse;
        try {
            xmlResponse = restTemplate.getForObject(validateUrl, String.class);
        } catch (Exception e) {
            log.error("[CAS] 调用 CAS serviceValidate 失败 url={}", validateUrl, e);
            throw new BusinessException(ErrorCode.CAS_VALIDATION_FAILED, "无法连接CAS服务器，请稍后重试或联系管理员", e);
        }

        if (xmlResponse == null || xmlResponse.isBlank()) {
            log.error("[CAS] CAS 返回空响应");
            throw new BusinessException(ErrorCode.CAS_VALIDATION_FAILED, "CAS服务器返回空响应");
        }

        log.debug("[CAS] CAS 响应 XML: {}", xmlResponse);
        String username = parseCasUsername(xmlResponse);
        log.info("[CAS] 票据验证成功，CAS用户名={}", username);
        return username;
    }

    private String parseCasUsername(String xmlResponse) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            Document doc = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xmlResponse)));

            NodeList failureNodes = doc.getElementsByTagNameNS("http://www.yale.edu/tp/cas", "authenticationFailure");
            if (failureNodes.getLength() > 0) {
                String failureMsg = failureNodes.item(0).getTextContent().trim();
                log.warn("[CAS] 票据验证失败: {}", failureMsg);
                throw new BusinessException(ErrorCode.CAS_VALIDATION_FAILED, "CAS票据验证失败: " + failureMsg);
            }

            NodeList userNodes = doc.getElementsByTagNameNS("http://www.yale.edu/tp/cas", "user");
            if (userNodes.getLength() > 0) {
                String username = userNodes.item(0).getTextContent().trim();
                if (!username.isEmpty()) {
                    return username;
                }
            }

            NodeList userNodesNoNs = doc.getElementsByTagName("cas:user");
            if (userNodesNoNs.getLength() > 0) {
                String username = userNodesNoNs.item(0).getTextContent().trim();
                if (!username.isEmpty()) {
                    return username;
                }
            }

            log.error("[CAS] 无法从响应中提取用户名, XML={}", xmlResponse);
            throw new BusinessException(ErrorCode.CAS_VALIDATION_FAILED, "CAS响应中未找到用户信息");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[CAS] 解析CAS XML响应失败", e);
            throw new BusinessException(ErrorCode.CAS_VALIDATION_FAILED, "CAS认证响应格式异常，请联系管理员", e);
        }
    }
}
