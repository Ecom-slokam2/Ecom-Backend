package com.metaarch.catalog.store;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
@SpringBootTest(properties = "app.jwt.secret=local-isolated-test-secret-that-is-long-enough-for-hmac-sha256") @Transactional
class StoreDeliveryMappingIntegrationTest {
  @Autowired StoreRepository stores;@Autowired StoreDeliveryZoneRepository zones;
  @Autowired StoreDeliveryService service;@Autowired EntityManager entities;
  Store store(String city){var s=new Store();s.setId(UUID.randomUUID().toString());s.setName(s.getId());s.setLocation(city);s.setAddress("Test address");s.setLatitude(new BigDecimal("17.4"));s.setLongitude(new BigDecimal("78.4"));return stores.saveAndFlush(s);}
  StoreDeliveryZone zone(Store store,String location){var z=new StoreDeliveryZone();z.setStoreId(store.getId());z.setLocationId(location);z.setMaxRadiusKm(BigDecimal.TEN);return zones.saveAndFlush(z);}
  StoreDeliveryZoneRequest request(){return new StoreDeliveryZoneRequest(BigDecimal.ZERO,BigDecimal.TEN,25,40,new BigDecimal("30"),null,BigDecimal.ZERO,"Active",List.of());}
  @Test void radiusCreationAndLegacyRetryResolvePersistedRegionMapping(){
    var reference=store("Hyderabad");zone(reference,"hyderabad");var target=store(" Hyderabad ");
    var otherRegion=store("Chennai");zone(otherRegion,"chennai");
    var created=service.createZone(target.getId(),request());entities.flush();entities.clear();
    assertThat(zones.findById(created.id()).orElseThrow().getLocationId()).isEqualTo("hyderabad");
    var legacy=zone(target,null);var legacyId=legacy.getId();entities.clear();
    assertThat(service.getZoneLocation(legacyId).locationId()).isEqualTo("hyderabad");
    assertThat(zones.findById(legacyId).orElseThrow().getLocationId()).isNull();
    service.updateZone(target.getId(),legacyId,request());entities.flush();entities.clear();
    assertThat(zones.findById(legacyId).orElseThrow().getLocationId()).isEqualTo("hyderabad");
  }
  @Test void everyStoreResolvesOnlyItsOwnRegionAcrossCreateReadAndEdit(){
    String prefix=UUID.randomUUID().toString();
    for(String region:List.of("Hyderabad","Chennai","Bengaluru")){
      String city=prefix+region;
      String location=prefix+"-"+region.toLowerCase(Locale.ROOT);
      zone(store(city),location);
      for(int index=0;index<2;index++){
        var target=store(" "+city.toUpperCase(Locale.ROOT)+" ");
        var legacy=zone(target,null);Long legacyId=legacy.getId();entities.clear();
        assertThat(service.getZoneLocation(legacyId).locationId()).isEqualTo(location);
        assertThat(zones.findById(legacyId).orElseThrow().getLocationId()).isNull();
        var created=service.createZone(target.getId(),request());
        service.updateZone(target.getId(),legacyId,request());entities.flush();entities.clear();
        assertThat(zones.findById(created.id()).orElseThrow().getLocationId()).isEqualTo(location);
        assertThat(zones.findById(legacyId).orElseThrow().getLocationId()).isEqualTo(location);
      }
    }
  }
  @Test void unrelatedRegionCannotSupplyMissingStoreMapping(){
    String prefix=UUID.randomUUID().toString();zone(store(prefix+"Hyderabad"),prefix+"-hyderabad");
    var target=store(prefix+"Unconfigured");var legacy=zone(target,null);
    assertThatThrownBy(()->service.getZoneLocation(legacy.getId())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing or ambiguous");
    assertThatThrownBy(()->service.createZone(target.getId(),request())).isInstanceOf(IllegalArgumentException.class);
  }
}
