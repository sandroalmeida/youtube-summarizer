package com.sandroalmeida.youtubesummarizer.model;

public class AccountInfo {
    private String name;
    private String handle;
    private String profileImageUrl;

    public AccountInfo() {
    }

    public AccountInfo(String name, String handle, String profileImageUrl) {
        this.name = name;
        this.handle = handle;
        this.profileImageUrl = profileImageUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
