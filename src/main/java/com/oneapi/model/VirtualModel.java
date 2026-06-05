package com.oneapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VirtualModel {
    private int id;
    private String name;
    private String match;

    public VirtualModel() {}

    /** 哨兵值，防止 Caffeine 缓存空查询结果。 */
    public static final VirtualModel NOT_FOUND = new VirtualModel();

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMatch() { return match; }
    public void setMatch(String match) { this.match = match; }
}
