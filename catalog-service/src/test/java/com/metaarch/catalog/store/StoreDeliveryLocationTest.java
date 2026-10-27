package com.metaarch.catalog.store;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
class StoreDeliveryLocationTest {
  final StoreRepository stores=mock(StoreRepository.class);
  final StoreDeliveryZoneRepository zones=mock(StoreDeliveryZoneRepository.class);
  final StoreDeliveryMethodRepository methods=mock(StoreDeliveryMethodRepository.class);
  final StoreDeliveryService service=new StoreDeliveryService(stores,zones,methods,mock(OpenRouteServiceClient.class));
  StoreDeliveryZone zone(long id,String location){var z=new StoreDeliveryZone();ReflectionTestUtils.setField(z,"id",id);z.setStoreId("main");z.setLocationId(location);return z;}
  StoreDeliveryZoneRequest request(){return new StoreDeliveryZoneRequest(BigDecimal.ZERO,BigDecimal.TEN,25,40,new BigDecimal("30"),new BigDecimal("499"),BigDecimal.ZERO,"Active",List.of());}
  void store(){var store=new Store();store.setId("main");store.setLocation("Hyderabad");when(stores.findById("main")).thenReturn(Optional.of(store));}
  @Test void newRadiusInheritsExistingStoreMappingAndFees(){
    store();when(zones.findByStoreIdOrderByMinRadiusKmAsc("main")).thenReturn(List.of(zone(1,"hyderabad")));
    when(zones.save(any())).thenAnswer(call->call.getArgument(0));
    service.createZone("main",request());
    var saved=org.mockito.ArgumentCaptor.forClass(StoreDeliveryZone.class);verify(zones).save(saved.capture());
    assertThat(saved.getValue().getLocationId()).isEqualTo("hyderabad");assertThat(saved.getValue().getDeliveryCharge()).isEqualByComparingTo("30");
  }
  @Test void legacyZone18CanResolveWithoutProductionBackfill(){
    var legacy=zone(18,null);when(zones.findById(18L)).thenReturn(Optional.of(legacy));
    when(zones.findByStoreIdOrderByMinRadiusKmAsc("main")).thenReturn(List.of(legacy,zone(2,"hyderabad")));
    assertThat(service.getZoneLocation(18L).locationId()).isEqualTo("hyderabad");
    assertThat(legacy.getLocationId()).isNull();verify(zones,never()).save(any());
  }
  @Test void unmappedStoreUsesOnlyItsConfiguredRegionsConsistentMappings(){
    store();var legacy=zone(18,null);when(zones.findById(18L)).thenReturn(Optional.of(legacy));
    when(zones.findMappedLocationIdsForStoreRegion("Hyderabad")).thenReturn(List.of("hyderabad"));
    assertThat(service.getZoneLocation(18L).locationId()).isEqualTo("hyderabad");
  }
  @Test void savingLegacyRadiusPersistsItsInheritedMapping(){
    store();var legacy=zone(18,null);when(zones.findById(18L)).thenReturn(Optional.of(legacy));
    when(zones.findByStoreIdOrderByMinRadiusKmAsc("main")).thenReturn(List.of(legacy,zone(2,"hyderabad")));
    service.updateZone("main",18L,request());assertThat(legacy.getLocationId()).isEqualTo("hyderabad");
  }
  @Test void conflictingStoreMappingsFailClosed(){
    var legacy=zone(18,null);when(zones.findById(18L)).thenReturn(Optional.of(legacy));
    when(zones.findByStoreIdOrderByMinRadiusKmAsc("main")).thenReturn(List.of(zone(1,"hyderabad"),zone(2,"chennai")));
    assertThatThrownBy(()->service.getZoneLocation(18L)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("conflict");
  }
  @Test void missingOrConflictingRegionMappingsCannotInventAssignments(){
    store();var store=stores.findById("main").orElseThrow();store.setLocation("Unmapped Region");
    var legacy=zone(18,null);when(zones.findById(18L)).thenReturn(Optional.of(legacy));
    assertThatThrownBy(()->service.getZoneLocation(18L)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mapping");
    when(zones.findMappedLocationIdsForStoreRegion("Hyderabad")).thenReturn(List.of("hyderabad","another-location"));
    assertThatThrownBy(()->service.getZoneLocation(18L)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ambiguous");
  }
  @Test void explicitMappingIsPreserved(){
    when(zones.findById(18L)).thenReturn(Optional.of(zone(18,"hyderabad")));
    assertThat(service.getZoneLocation(18L).locationId()).isEqualTo("hyderabad");verifyNoInteractions(stores);
  }
}
