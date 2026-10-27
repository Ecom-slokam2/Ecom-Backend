package com.metaarch.order.order;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface OrderRepository extends JpaRepository<Order, UUID> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.id = :id")
  Optional<Order> findLockedById(@org.springframework.data.repository.query.Param("id") UUID id);
  List<Order> findAllByOrderByCreatedAtDesc();
  List<Order> findByStoreIdOrderByCreatedAtDesc(String storeId);
  List<Order> findByStoreIdInOrderByCreatedAtDesc(java.util.Collection<String> storeIds);
  List<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
  List<Order> findByCustomerIdAndStatusOrderByCreatedAtDesc(UUID customerId, OrderStatus status);
  boolean existsByOrderNumber(String orderNumber);
  List<Order> findByCartId(UUID cartId);
}
