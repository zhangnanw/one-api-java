package com.oneapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Vendor {
    private int id;
    private String name;
    private String description;
    private int status;
    private String group;
    private int priority;
    @JsonProperty("created_time")
    private long createdTime;
    @JsonProperty("base_url")
    private String baseUrl;
    @JsonProperty("api_key")
    private String apiKey;
    private String meta;

    public Vendor() {}

}
