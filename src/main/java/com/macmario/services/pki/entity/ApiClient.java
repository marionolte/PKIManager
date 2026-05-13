package com.macmario.services.pki.entity;

import java.time.LocalDateTime;

public class ApiClient {
    private Long id;
    private String name;
    private String apiKey;
    private String description;
    private boolean active = true;
    private Long defaultCaId;
    private String defaultCaName;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String v) { apiKey = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { active = v; }
    public Long getDefaultCaId() { return defaultCaId; }
    public void setDefaultCaId(Long v) { defaultCaId = v; }
    public String getDefaultCaName() { return defaultCaName; }
    public void setDefaultCaName(String v) { defaultCaName = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}
