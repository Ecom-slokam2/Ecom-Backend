package com.metaarch.order.order;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.metaarch.order.event.OrderEventPublisher;
import com.metaarch.order.exception.ConflictException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class GlobalStoreAssignmentTest {
  private final OrderRepository orders = mock(OrderRepository.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
  private final GlobalStoreAssignmentCatalogClient catalog = mock(GlobalStoreAssignmentCatalogClient.class);
  private final GlobalStoreAssignmentInventoryClient inventory = mock(GlobalStoreAssignmentInventoryClient.class);
  private final OrderService service = new OrderService(orders, mock(OrderItemRepository.class), mock(OrderEventPublisher.class),
      mock(CatalogDeliveryQuoteClient.class), catalog, inventory, mock(FulfillmentLifecycleVerifier.class));

  @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

  @Test void authorizedGlobalStoreAssignmentPersistsAndCanBeReadBack() {
    var order = globalOrder();
    when(orders.findLockedById(order.getId())).thenReturn(java.util.Optional.of(order));
    when(orders.findById(order.getId())).thenReturn(java.util.Optional.of(order));
    when(catalog.store("store-hyd")).thenReturn(new GlobalStoreAssignmentCatalogClient.Store("store-hyd", true, "Active"));
    when(inventory.inventory(any(), any(), eq("store-hyd"))).thenReturn(new GlobalStoreAssignmentInventoryClient.Inventory("store-hyd", "ACTIVE", 4, 0));

    OrderResponse assigned = service.assignGlobalStore(order.getId(), "store-hyd");
    OrderResponse refreshed = service.get(order.getId());

    assertThat(assigned.globalAssignedStoreId()).isEqualTo("store-hyd");
    assertThat(assigned.globalApproval()).isEqualTo("Approved");
    assertThat(refreshed.globalAssignedStoreId()).isEqualTo("store-hyd");
    assertThat(refreshed.status()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @Test void rejectsInsufficientStock() {
    var order = globalOrder();
    when(orders.findLockedById(order.getId())).thenReturn(java.util.Optional.of(order));
    when(catalog.store("store-hyd")).thenReturn(new GlobalStoreAssignmentCatalogClient.Store("store-hyd", true, "Active"));
    when(inventory.inventory(any(), any(), eq("store-hyd"))).thenReturn(new GlobalStoreAssignmentInventoryClient.Inventory("store-hyd", "ACTIVE", 1, 0));
    assertThatThrownBy(() -> service.assignGlobalStore(order.getId(), "store-hyd")).isInstanceOf(ConflictException.class);
    assertThat(order.getGlobalAssignedStoreId()).isNull();
  }

  @Test void rejectsInactiveStoreAndNonGlobalOrder() {
    var order = globalOrder();
    when(orders.findLockedById(order.getId())).thenReturn(java.util.Optional.of(order));
    when(catalog.store("store-hyd")).thenReturn(new GlobalStoreAssignmentCatalogClient.Store("store-hyd", false, "Inactive"));
    assertThatThrownBy(() -> service.assignGlobalStore(order.getId(), "store-hyd")).isInstanceOf(IllegalArgumentException.class);
    var local = new Order(UUID.randomUUID(), UUID.randomUUID(), "store-hyd", "LOCAL-1");
    org.springframework.test.util.ReflectionTestUtils.setField(local, "id", UUID.randomUUID());
    local.setFulfillmentType("global");
    local.setDeliveryMethod("global");
    when(orders.findLockedById(local.getId())).thenReturn(java.util.Optional.of(local));
    assertThatThrownBy(() -> service.assignGlobalStore(local.getId(), "store-hyd")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test void rejectsRepeatedAssignmentWithoutReplacingSelectedStore() {
    var order = globalOrder();
    order.setGlobalApproval("Approved");
    order.setGlobalAssignedStoreId("store-existing");
    when(orders.findLockedById(order.getId())).thenReturn(java.util.Optional.of(order));
    assertThatThrownBy(() -> service.assignGlobalStore(order.getId(), "store-hyd")).isInstanceOf(ConflictException.class);
    assertThat(order.getGlobalAssignedStoreId()).isEqualTo("store-existing");
  }

  @Test void assignmentAuthorizationAcceptsOnlySuperAndGlobalAdmin() {
    var access = new OrderAccess(orders, org.mockito.Mockito.mock(com.metaarch.sync.CatalogLocationClient.class));
    assertThat(authorization(access, "ROLE_SUPER_ADMIN")).isTrue();
    assertThat(authorization(access, "ROLE_GLOBAL_ADMIN")).isTrue();
    assertThat(authorization(access, "ROLE_STORE_MANAGER")).isFalse();
  }

  private boolean authorization(OrderAccess access, String authority) {
    Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").claim("sub", "admin").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of(new SimpleGrantedAuthority(authority))));
    return access.globalAssignment();
  }

  private Order globalOrder() {
    var order = new Order(UUID.randomUUID(), UUID.randomUUID(), "global-store", "GLOBAL-1");
    org.springframework.test.util.ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
    order.setFulfillmentType("global");
    order.setGlobalApproval("Pending");
    order.addItem(new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "Pickle jar", 3, BigDecimal.TEN));
    return order;
  }
}
