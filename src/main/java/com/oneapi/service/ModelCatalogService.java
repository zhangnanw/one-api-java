package com.oneapi.service;

import com.oneapi.repository.ModelCatalogRepository;
import com.oneapi.model.ModelCatalogEntry;
import com.oneapi.repo.CapabilityCatalog;
import com.oneapi.repo.WindowCatalog;
import io.vertx.core.json.JsonArray;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ModelCatalogService implements CapabilityCatalog, WindowCatalog {

    private static final String CACHE = "modelCatalog";

    private final ModelCatalogRepository jpaRepository;

    public ModelCatalogService(ModelCatalogRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Cacheable(value = CACHE, key = "'all'")
    public List<ModelCatalogEntry> findAll() {
        return jpaRepository.findAll();
    }

    @Cacheable(value = CACHE, key = "#name")
    public ModelCatalogEntry findByName(String name) {
        return jpaRepository.findById(name).orElse(null);
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public void insert(ModelCatalogEntry entry) {
        jpaRepository.save(entry);
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public void update(String name, ModelCatalogEntry entry) {
        entry.setName(name);
        jpaRepository.save(entry);
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public void delete(String name) {
        jpaRepository.deleteById(name);
    }

    @Override
    @Cacheable(value = CACHE, key = "#modelName + '_caps'")
    public List<String> getCapabilities(String modelName) {
        ModelCatalogEntry entry = findByName(modelName);
        if (entry == null || entry.getCapabilities() == null) {
            return Collections.emptyList();
        }
        try {
            JsonArray arr = new JsonArray(entry.getCapabilities());
            List<String> caps = new ArrayList<>(arr.size());
            for (int i = 0; i < arr.size(); i++) {
                caps.add(arr.getString(i));
            }
            return Collections.unmodifiableList(caps);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean hasCapability(String modelName, String capability) {
        if (modelName == null || capability == null) return false;
        return getCapabilities(modelName).stream()
            .anyMatch(c -> c.equalsIgnoreCase(capability));
    }

    @Override
    @Cacheable(value = CACHE, key = "#modelName + '_window'")
    public int getContextWindow(String modelName) {
        if (modelName == null) return 0;
        ModelCatalogEntry entry = findByName(modelName);
        return entry != null && entry.getContextWindow() != null ? entry.getContextWindow() : 0;
    }
}
