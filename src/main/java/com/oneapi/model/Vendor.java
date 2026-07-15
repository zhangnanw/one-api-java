package com.oneapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = "apiKey")
@ToString(exclude = "apiKey")
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "vendors")
public class Vendor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String name;
    private String description;
    private int status;

    @Column(name = "\"group\"")
    private String group;

    private int priority;

    @JsonProperty("created_time")
    @Column(name = "created_time")
    private long createdTime;

    @JsonProperty("base_url")
    @Column(name = "base_url")
    private String baseUrl;

    @JsonProperty("api_key")
    @Column(name = "api_key")
    private String apiKey;

    @JsonProperty("balance_credential")
    @Column(name = "balance_credential")
    private String balanceCredential;

    private String meta;
}
