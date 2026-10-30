package com.planner.route_planner_api.feature.customers;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

    // Only the items that belong to this company; ids from other companies are simply not returned
    List<Item> findByCompanyIdAndIdIn(Long companyId, Collection<Long> ids);

    Page<Item> findByCompanyId(Long companyId, Pageable pageable);

    boolean existsByCompanyIdAndSku(Long companyId, String sku);

    // Same, ignoring one item (the one being updated)
    boolean existsByCompanyIdAndSkuAndIdNot(Long companyId, String sku, Long id);
}
