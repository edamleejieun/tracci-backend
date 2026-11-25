package com.tracci.module.demo.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Component
@ConfigurationProperties("tracci.demo.journey")
public class DemoJourneyProperties {

    /** Keep disabled until production Demo COS assets are uploaded and approved. */
    private boolean enabled = false;

    private int schemaVersion = 1;

    private String contentVersion = "2026.07.31.7";

    /** Object-key prefix of the approved Demo-only directory in the configured COS bucket. */
    private String cosObjectKeyPrefix = "";

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public void setContentVersion(String contentVersion) {
        this.contentVersion = contentVersion;
    }

    public void setCosObjectKeyPrefix(String cosObjectKeyPrefix) {
        this.cosObjectKeyPrefix = cosObjectKeyPrefix;
    }
}
