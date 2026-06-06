package com.oneapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
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

    // 瞬时字段（非持久化）
    private Vendor vendor;

    public Instance() {}

}
