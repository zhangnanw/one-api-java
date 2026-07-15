package com.oneapi.jpa;

import com.oneapi.model.Instance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstanceJpaRepository extends JpaRepository<Instance, Integer> {

    List<Instance> findByModelName(String modelName);

    List<Instance> findByVendorId(int vendorId);

    List<Instance> findByStatusNotIn(List<Integer> statuses);

    List<Instance> findByModelNameAndStatusNotIn(String modelName, List<Integer> statuses);

    boolean existsByModelNameAndStatusIn(String modelName, List<Integer> statuses);

    @Query("""
        SELECT i FROM Instance i
        LEFT JOIN FETCH i.vendor
        WHERE i.status NOT IN :excludedStatuses
        ORDER BY i.id
        """)
    List<Instance> findAllWithVendor(@Param("excludedStatuses") List<Integer> excludedStatuses);
}
