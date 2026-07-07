package com.oneapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelCatalogEntry {
    private String name;

    private String capabilities;

    @JsonProperty("context_window")
    private Integer contextWindow;

    @JsonProperty("input_price")
    private Double inputPrice;

    @JsonProperty("output_price")
    private Double outputPrice;

    public ModelCatalogEntry() {}
}
