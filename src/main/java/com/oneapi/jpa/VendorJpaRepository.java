package com.oneapi.jpa;

import com.oneapi.model.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VendorJpaRepository extends JpaRepository<Vendor, Integer> {

    List<Vendor> findByStatus(int status);

    @Query(value = """
        SELECT v.id, v.name, v.description, v.status,
               v."group", v.priority, v.created_time, v.base_url,
               v.api_key, v.balance_credential, v.meta,
               COUNT(i.id) AS instance_count
        FROM vendors v
        LEFT JOIN instances i ON v.id = i.vendor_id AND i.status NOT IN (0, 3, 4, 5)
        GROUP BY v.id, v.name, v.description, v.status,
                 v."group", v.priority, v.created_time, v.base_url,
                 v.api_key, v.balance_credential, v.meta
        ORDER BY v.id
        LIMIT :pageSize OFFSET :offset
        """, nativeQuery = true)
    List<VendorWithCountProjection> findAllWithCounts(@Param("offset") int offset, @Param("pageSize") int pageSize);

    interface VendorWithCountProjection {
        int getId();
        String getName();
        String getDescription();
        int getStatus();
        String getGroup();
        int getPriority();
        long getCreated_time();
        String getBase_url();
        String getApi_key();
        String getBalance_credential();
        String getMeta();
        long getInstance_count();
    }
}
