package com.oneapi.service;

import com.oneapi.repository.VendorRepository;
import com.oneapi.model.Vendor;
import com.oneapi.model.VendorWithCount;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class VendorService {

    private static final String CACHE = "vendors";

    private final VendorRepository jpaRepository;

    public VendorService(VendorRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Cacheable(value = CACHE, key = "'all'")
    public List<Vendor> findAll() {
        return jpaRepository.findAll();
    }

    @Cacheable(value = CACHE, key = "'active'")
    public List<Vendor> findAllActive() {
        return jpaRepository.findByStatus(1);
    }

    public List<VendorWithCount> findAllWithCounts(int offset, int pageSize) {
        var projections = jpaRepository.findAllWithCounts(offset, pageSize);
        var result = new ArrayList<VendorWithCount>(projections.size());
        for (var p : projections) {
            Vendor vendor = new Vendor();
            vendor.setId(p.getId());
            vendor.setName(p.getName());
            vendor.setDescription(p.getDescription());
            vendor.setStatus(p.getStatus());
            vendor.setGroup(p.getGroup());
            vendor.setPriority(p.getPriority());
            vendor.setCreatedTime(p.getCreatedTime());
            vendor.setBaseUrl(p.getBaseUrl());
            vendor.setApiKey(p.getApiKey());
            vendor.setBalanceCredential(p.getBalanceCredential());
            vendor.setMeta(p.getMeta());
            result.add(new VendorWithCount(vendor, (int) p.getInstanceCount()));
        }
        return result;
    }

    @Cacheable(value = CACHE, key = "#id")
    public Vendor findById(int id) {
        return jpaRepository.findById(id).orElse(null);
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public void insert(Vendor vendor) {
        jpaRepository.save(vendor);
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public void update(int id, Vendor vendor) {
        vendor.setId(id);
        jpaRepository.save(vendor);
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public void updateApiKey(int id, String apiKey) {
        Vendor vendor = findById(id);
        if (vendor == null) {
            throw new RuntimeException("vendor not found: " + id);
        }
        vendor.setApiKey(apiKey);
        jpaRepository.save(vendor);
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public void delete(int id) {
        jpaRepository.deleteById(id);
    }
}
