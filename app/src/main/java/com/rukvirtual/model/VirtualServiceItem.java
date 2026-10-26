package com.rukvirtual.model;

public class VirtualServiceItem {
    private String serviceName;
    private String status;
    private String description;

    public VirtualServiceItem(String serviceName, String status, String description) {
        this.serviceName = serviceName;
        this.status = status;
        this.description = description;
    }

    public String getServiceName() { return serviceName; }
    public String getStatus() { return status; }
    public String getDescription() { return description; }
}