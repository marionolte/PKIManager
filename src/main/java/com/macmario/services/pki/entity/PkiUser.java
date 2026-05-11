package com.macmario.services.pki.entity;

import java.time.LocalDateTime;

public class PkiUser {
    public enum Role { ADMIN, VIEWER }

    private Long id;
    private String username;
    private String passwordHash;
    private String salt;
    private String displayName;
    private String email;
    private Role role = Role.VIEWER;
    private boolean active = true;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lastLoginAt;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getUsername() { return username; }
    public void setUsername(String v) { username = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { passwordHash = v; }
    public String getSalt() { return salt; }
    public void setSalt(String v) { salt = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { displayName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = v; }
    public Role getRole() { return role; }
    public void setRole(Role v) { role = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { active = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime v) { lastLoginAt = v; }

    public boolean isAdmin() { return role == Role.ADMIN; }
}
