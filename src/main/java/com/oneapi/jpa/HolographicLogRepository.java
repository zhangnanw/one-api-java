package com.oneapi.jpa;

import com.oneapi.model.HolographicLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HolographicLogRepository extends JpaRepository<HolographicLogEntity, Long> {
}
