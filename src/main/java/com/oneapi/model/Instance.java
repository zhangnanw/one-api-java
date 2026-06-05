package com.oneapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Instance {
    private int id;
    @JsonProperty("model_name")
    private String modelName;
    private int status;
    @JsonProperty("upstream_model")
    private String upstreamModel;
    @JsonProperty("vendor_id")
    private int vendorId;
    @JsonProperty("created_time")
    private long createdTime;
    private String meta;

    // Transient
    private Vendor vendor;

    public Instance() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getUpstreamModel() { return upstreamModel; }
    public void setUpstreamModel(String upstreamModel) { this.upstreamModel = upstreamModel; }

    public int getVendorId() { return vendorId; }
    public void setVendorId(int vendorId) { this.vendorId = vendorId; }

    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }

    public String getMeta() { return meta; }
    public void setMeta(String meta) { this.meta = meta; }

    public Vendor getVendor() { return vendor; }
    public void setVendor(Vendor vendor) { this.vendor = vendor; }
}
