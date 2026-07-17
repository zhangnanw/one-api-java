package com.oneapi.repository;

import com.oneapi.entity.ModelCatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelCatalogRepository extends JpaRepository<ModelCatalogEntry, String> {
}
