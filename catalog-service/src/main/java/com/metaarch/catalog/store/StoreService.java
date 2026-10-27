package com.metaarch.catalog.store;

import com.metaarch.catalog.exception.ConflictException;
import com.metaarch.catalog.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {
  private final StoreRepository stores;
  private final StoreDeliveryService delivery;
  public StoreService(StoreRepository stores, StoreDeliveryService delivery) { this.stores = stores; this.delivery = delivery; }

  @Transactional
  public StoreResponse create(StoreRequest request) {
    String id = clean(request.id());
    if (stores.existsById(id)) throw new ConflictException("Store ID already exists");
    if (stores.existsByNameIgnoreCase(clean(request.name()))) throw new ConflictException("Store name already exists");
    Store store = new Store(id);
    apply(store, request);
    Store saved = stores.save(store);
    delivery.ensureDefaultZone(saved);
    return StoreResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public List<StoreResponse> list(Boolean active) {
    List<Store> result = (active == null ? stores.findAll() : stores.findByActive(active)).stream()
        .filter(store -> !store.getId().startsWith("location-")).toList();
    return result.stream().map(StoreResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<StoreResponse> listLocations(Boolean active) {
    return (active == null ? stores.findAll() : stores.findByActive(active)).stream()
        .filter(store -> store.getId().startsWith("location-"))
        .map(StoreResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public StoreResponse get(String id) { return StoreResponse.from(find(id)); }

  @Transactional
  public StoreResponse update(String id, StoreRequest request) {
    Store store = find(id);
    String name = clean(request.name());
    if (stores.existsByNameIgnoreCaseAndIdNot(name, id)) throw new ConflictException("Store name already exists");
    apply(store, request);
    delivery.ensureDefaultZone(store);
    return StoreResponse.from(store);
  }

  @Transactional
  public void delete(String id) { stores.delete(find(id)); }

  private Store find(String id) {
    return stores.findById(clean(id)).orElseThrow(() -> new ResourceNotFoundException("Store not found: " + id));
  }

  private void apply(Store store, StoreRequest request) {
    store.setName(clean(request.name()));
    store.setStoreCode(blank(request.storeCode()));
    store.setLogoUrl(blank(request.logoUrl()));
    store.setDescription(blank(request.description()));
    store.setLocation(clean(request.location()));
    store.setAddress(clean(request.address()));
    store.setDoorNumber(blank(request.doorNumber()));
    store.setStreetName(blank(request.streetName()));
    store.setArea(blank(request.area()));
    store.setLandmark(blank(request.landmark()));
    store.setCity(blank(request.city()));
    store.setDistrict(blank(request.district()));
    store.setState(blank(request.state()));
    store.setPincode(blank(request.pincode()));
    store.setLatitude(scale(request.latitude()));
    store.setLongitude(scale(request.longitude()));
    store.setProvider(blank(request.provider()) == null ? "OPENSTREETMAP" : clean(request.provider()).toUpperCase());
    store.setProviderPlaceId(blank(request.providerPlaceId()));
    store.setProviderOsmType(blank(request.providerOsmType()));
    store.setProviderOsmId(blank(request.providerOsmId()));
    store.setLocationAccuracyMeters(request.locationAccuracyMeters());
    store.setPhone(blank(request.phone()));
    store.setAlternatePhone(blank(request.alternatePhone()));
    store.setEmail(blank(request.email()));
    store.setAdminName(blank(request.adminName()));
    store.setStoreRating(request.storeRating());
    store.setGstNumber(blank(request.gstNumber()));
    store.setStatus(blank(request.status()) == null ? (request.active() ? "Active" : "Inactive") : blank(request.status()));
    store.setDeliveryEnabled(request.deliveryEnabled());
    store.setPickupEnabled(request.pickupEnabled());
    store.setDeliveryRadiusKm(request.deliveryRadiusKm());
    store.setDeliveryEtaMinutes(request.deliveryEtaMinutes());
    store.setDeliveryCharge(request.deliveryCharge());
    store.setFreeDeliveryAbove(request.freeDeliveryAbove());
    store.setDeliveryModes(blank(request.deliveryModes()));
    store.setDeliveryNotes(blank(request.deliveryNotes()));
    store.setNew(request.isNew());
    store.setActive(request.active());
  }

  private BigDecimal scale(BigDecimal value) { return value.setScale(7, RoundingMode.HALF_UP); }
  private String clean(String value) { return value.trim(); }
  private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
