package com.microcourse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class CasSettingsDTO {

    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonProperty("serverUrl")
    private String serverUrl;

    @JsonProperty("serviceUrl")
    private String serviceUrl;

    @JsonProperty("version")
    private String version;

    @JsonProperty("adminUsername")
    private String adminUsername;

    @JsonProperty("superAdmins")
    private List<String> superAdmins;

    @JsonProperty("validateSsl")
    private Boolean validateSsl;

    public CasSettingsDTO() {}

    public Boolean getEnabled() {
        return enabled != null ? enabled : false;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public String getVersion() {
        return version != null ? version : "3.0";
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public List<String> getSuperAdmins() {
        return superAdmins;
    }

    public Boolean getValidateSsl() {
        return validateSsl != null ? validateSsl : true;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public void setSuperAdmins(List<String> superAdmins) {
        this.superAdmins = superAdmins;
    }

    public void setValidateSsl(Boolean validateSsl) {
        this.validateSsl = validateSsl;
    }
}
