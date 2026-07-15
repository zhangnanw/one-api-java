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
@EqualsAndHashCode(exclude = "vendor")
@ToString(exclude = "vendor")
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "instances")
public class Instance {
    public static final int STATUS_RAW = 1;
    public static final int STATUS_TAGGED = 2;
    public static final int STATUS_DISABLED = 3;
    public static final int STATUS_DEPRECATED = 4;
    public static final int STATUS_FAILED = 5;     // 上游持续失败，已标记为不可用
    public static final int STATUS_UNKNOWN = 0;    // 尚未探测或状态未知

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @JsonProperty("model_name")
    @Column(name = "model_name")
    private String modelName;

    private int status;

    @JsonProperty("upstream_model")
    @Column(name = "upstream_model")
    private String upstreamModel;

    @JsonProperty("vendor_id")
    @Column(name = "vendor_id")
    private int vendorId;

    @JsonProperty("created_time")
    @Column(name = "created_time")
    private long createdTime;

    private String meta;

    private float pref = 0.5f;

    private String layer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", insertable = false, updatable = false)
    private Vendor vendor;
}
