package com.oneapi.service;

import com.oneapi.repository.VirtualModelRepository;
import com.oneapi.entity.VirtualModel;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VirtualModelService {

    private static final String CACHE = "virtualModels";

    private final VirtualModelRepository jpaRepository;

    @Cacheable(value = CACHE, key = "'all'")
    public List<VirtualModel> findAll() {
        return jpaRepository.findAll();
    }

    @Cacheable(value = CACHE, key = "#id")
    public VirtualModel findById(int id) {
        return jpaRepository.findById(id).orElse(null);
    }

    @Cacheable(value = CACHE, key = "#name")
    public VirtualModel findByName(String name) {
        return jpaRepository.findByName(name).orElse(null);
    }

    @Transactional
    @CacheEvict(value = CACHE, allEntries = true)
    public void insert(VirtualModel virtualModel) {
        jpaRepository.save(virtualModel);
    }

    @Transactional
    @CacheEvict(value = CACHE, allEntries = true)
    public void updateMatch(int id, String match) {
        VirtualModel existing = findById(id);
        if (existing == null) {
            throw new RuntimeException("virtual model not found: " + id);
        }
        existing.setMatch(match);
        jpaRepository.save(existing);
    }

    @Transactional
    @CacheEvict(value = CACHE, allEntries = true)
    public void delete(int id) {
        jpaRepository.deleteById(id);
    }
}
