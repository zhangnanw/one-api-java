package com.oneapi.repository;

import com.oneapi.model.RelayLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RelayLogRepository extends JpaRepository<RelayLogEntity, Long> {
}
