package com.oneapi.repository;

import com.oneapi.model.VirtualModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * VirtualModel Repository.
 */
@Repository
public interface VirtualModelRepository extends JpaRepository<VirtualModel, Integer> {

    Optional<VirtualModel> findByName(String name);
}
