package com.oneapi.jpa;

import com.oneapi.model.ModelCatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelCatalogJpaRepository extends JpaRepository<ModelCatalogEntry, String> {
}
