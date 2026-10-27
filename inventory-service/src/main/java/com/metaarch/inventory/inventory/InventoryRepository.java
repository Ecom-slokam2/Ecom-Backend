package com.metaarch.inventory.inventory;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {
  Optional<Inventory> findByProductIdAndVariantKeyAndStoreId(UUID productId, UUID variantKey, String storeId);
  Page<Inventory> findByStoreId(String storeId, Pageable pageable);
  Page<Inventory> findByStoreIdIn(java.util.Collection<String> storeIds, Pageable pageable);
  Page<Inventory> findByStatus(InventoryStatus status, Pageable pageable);
  Page<Inventory> findByStoreIdAndStatus(String storeId, InventoryStatus status, Pageable pageable);
  Page<Inventory> findByStoreIdInAndStatus(java.util.Collection<String> storeIds, InventoryStatus status, Pageable pageable);
  boolean existsByProductIdAndVariantKeyAndStoreId(UUID productId, UUID variantKey, String storeId);
  boolean existsByProductIdAndVariantKeyAndStoreIdAndIdNot(UUID productId, UUID variantKey, String storeId, UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Inventory i where i.id = :id")
  Optional<Inventory> findByIdForUpdate(@Param("id") UUID id);
}
