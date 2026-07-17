package com.oneapi.repository;

import com.oneapi.entity.HolographicLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HolographicLogRepository extends JpaRepository<HolographicLog, Long> {
}
