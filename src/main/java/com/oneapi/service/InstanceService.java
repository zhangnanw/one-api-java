package com.oneapi.service;

import com.oneapi.repository.InstanceRepository;
import com.oneapi.repository.VendorRepository;
import com.oneapi.entity.Instance;
import com.oneapi.entity.Vendor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InstanceService {

    private static final String CACHE = "instances";

    private final InstanceRepository jpaRepository;
    private final VendorRepository vendorJpaRepository;

    @Cacheable(value = CACHE, key = "'all'")
    public List<Instance> findAll() {
        return jpaRepository.findByStatusNotIn(List.of(Instance.STATUS_DISABLED, Instance.STATUS_DEPRECATED));
    }

    @Cacheable(value = CACHE, key = "#id")
    public Instance findById(int id) {
        return jpaRepository.findById(id).orElse(null);
    }

    public List<Instance> findByVendorId(int vendorId) {
        return jpaRepository.findByVendorId(vendorId);
    }

    @CacheEvict(value = {CACHE, "routedInstances"}, allEntries = true)
    @Transactional
    public void insert(Instance instance) {
        if (instance.getCreatedTime() == 0) {
            instance.setCreatedTime(System.currentTimeMillis() / 1000);
        }
        if (instance.getMeta() == null) {
            instance.setMeta("{}");
        }
        if (instance.getLayer() == null) {
            instance.setLayer("payg");
        }
        jpaRepository.save(instance);
    }

    @CacheEvict(value = {CACHE, "routedInstances"}, allEntries = true)
    @Transactional
    public void update(Instance instance) {
        jpaRepository.save(instance);
    }

    @CacheEvict(value = {CACHE, "routedInstances"}, allEntries = true)
    @Transactional
    public void toggleStatus(int id) {
        Instance existing = findById(id);
        if (existing == null) {
            throw new RuntimeException("instance not found: " + id);
        }
        int newStatus = (existing.getStatus() == 1 || existing.getStatus() == 2) ? 3 : 2;
        existing.setStatus(newStatus);
        jpaRepository.save(existing);
    }

    @CacheEvict(value = {CACHE, "routedInstances"}, allEntries = true)
    @Transactional
    public void delete(int id) {
        jpaRepository.deleteById(id);
    }

    public boolean existsByModelName(String modelName) {
        return jpaRepository.existsByModelNameAndStatusIn(
            modelName, List.of(Instance.STATUS_RAW, Instance.STATUS_TAGGED));
    }

    public Vendor findVendorById(int vendorId) {
        return vendorJpaRepository.findById(vendorId).orElse(null);
    }
}
