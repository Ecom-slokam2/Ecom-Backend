package com.metaarch.catalog.store;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreDeliveryZoneRepository extends JpaRepository<StoreDeliveryZone, Long> {
  List<StoreDeliveryZone> findByStoreIdOrderByMinRadiusKmAsc(String storeId);
  @org.springframework.data.jpa.repository.Query("select distinct z.locationId from StoreDeliveryZone z, Store s where z.storeId = s.id and (lower(trim(s.location)) = lower(trim(:location)) or lower(trim(s.city)) = lower(trim(:location))) and z.locationId is not null and trim(z.locationId) <> ''")
  List<String> findMappedLocationIdsForStoreRegion(@org.springframework.data.repository.query.Param("location") String location);
}
