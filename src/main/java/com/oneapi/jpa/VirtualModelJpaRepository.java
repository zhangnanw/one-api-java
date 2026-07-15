package com.oneapi.jpa;

import com.oneapi.model.VirtualModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * VirtualModel JPA Repository（Step 6 先行迁移）。
 */
@Repository
public interface VirtualModelJpaRepository extends JpaRepository<VirtualModel, Integer> {

    Optional<VirtualModel> findByName(String name);
}
