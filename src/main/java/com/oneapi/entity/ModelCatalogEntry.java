package com.oneapi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "model_catalog")
public class ModelCatalogEntry {
    @Id
    private String name;

    private String capabilities;

    @JsonProperty("context_window")
    @Column(name = "context_window")
    private Integer contextWindow;

    @JsonProperty("input_price")
    @Column(name = "input_price")
    private Double inputPrice;

    @JsonProperty("output_price")
    @Column(name = "output_price")
    private Double outputPrice;

    @JsonProperty("reference_notes")
    @Column(name = "reference_notes")
    private String referenceNotes;
}
