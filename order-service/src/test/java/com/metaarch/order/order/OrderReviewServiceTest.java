package com.metaarch.order.order;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metaarch.order.exception.InvalidOrderStateException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class OrderReviewServiceTest {
  @Mock OrderRepository orders;
  @Mock JdbcTemplate jdbc;

  @ParameterizedTest
  @EnumSource(value = OrderStatus.class, names = "DELIVERED", mode = EnumSource.Mode.EXCLUDE)
  void rejectsReviewUntilOrderIsDelivered(OrderStatus status) {
    UUID orderId = UUID.randomUUID();
    Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), "store", "test-order");
    order.setStatus(status);
    when(orders.findLockedById(orderId)).thenReturn(Optional.of(order));

    OrderReviewService service = new OrderReviewService(orders, jdbc, new ObjectMapper());

    assertThatThrownBy(() -> service.save(orderId, new OrderReviewRequest(5, "Great service", List.of())))
      .isInstanceOf(InvalidOrderStateException.class)
      .hasMessage("A review can be submitted after delivery is complete.");
    verifyNoInteractions(jdbc);
  }

  @Test
  void deliveredOrderSavesReviewForItsPersistedCustomer() {
    UUID orderId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    Order order = new Order(customerId, UUID.randomUUID(), "store", "test-order");
    order.addItem(new OrderItem(productId, null, "Test product", 1, java.math.BigDecimal.ONE));
    order.setStatus(OrderStatus.DELIVERED);
    when(orders.findLockedById(orderId)).thenReturn(Optional.of(order));
    Instant now = java.time.Instant.now();
    OrderReviewResponse response = new OrderReviewResponse(UUID.randomUUID(), orderId, productId, "Test product",
      "test-order", 5, "Great service", List.of(), ReviewStatus.PENDING, null, now, now);
    OrderReviewService service = spy(new OrderReviewService(orders, jdbc, new ObjectMapper()));
    doReturn(Optional.of(response)).when(service).get(orderId);

    assertThat(service.save(orderId, new OrderReviewRequest(5, " Great service ", List.of()))).isEqualTo(response);
    verify(jdbc).update(anyString(), anyString(), eq(orderId.toString()), eq(customerId.toString()),
      eq(productId.toString()), eq("Test product"), eq(5), eq("Great service"), eq("[]"));
  }

  @Test
  void deliveredFulfillmentDoesNotAllowReviewOfProductOutsideOrder() {
    UUID orderId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    Order order = mock(Order.class);
    ReviewDeliveryVerifier delivery = mock(ReviewDeliveryVerifier.class);
    when(order.getStatus()).thenReturn(OrderStatus.CREATED);
    when(order.getItems()).thenReturn(List.of(new OrderItem(productId, null, "Seeded QA item", 1, java.math.BigDecimal.ONE)));
    when(orders.findLockedById(orderId)).thenReturn(Optional.of(order));
    when(delivery.isDelivered(orderId)).thenReturn(true);
    OrderReviewService service = new OrderReviewService(orders, jdbc, new ObjectMapper(), delivery);

    assertThatThrownBy(() -> service.save(orderId, new OrderReviewRequest(UUID.randomUUID(), 5, "Good", List.of())))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Choose a product from this order.");
    verify(delivery).isDelivered(orderId);
    verifyNoInteractions(jdbc);
  }

  @Test
  void legacyOrderLevelReviewCanBeReadWithoutAProductAssociation() throws Exception {
    ResultSet row = mock(ResultSet.class);
    UUID reviewId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Instant now = Instant.parse("2026-10-24T08:00:00Z");
    when(row.getString("photo_urls")).thenReturn("[]");
    when(row.getString("id")).thenReturn(reviewId.toString());
    when(row.getString("order_id")).thenReturn(orderId.toString());
    when(row.getString("product_id")).thenReturn(null);
    when(row.getString("product_name")).thenReturn(null);
    when(row.getString("order_number")).thenReturn("QA-ORDER");
    when(row.getString("comment")).thenReturn("Legacy review");
    when(row.getString("status")).thenReturn("PENDING");
    when(row.getString("admin_reply")).thenReturn(null);
    when(row.getInt("rating")).thenReturn(4);
    when(row.getTimestamp("created_at")).thenReturn(Timestamp.from(now));
    when(row.getTimestamp("updated_at")).thenReturn(Timestamp.from(now));

    OrderReviewService service = new OrderReviewService(orders, jdbc, new ObjectMapper());
    OrderReviewResponse response = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "mapReview", row, 0);

    assertThat(response).isNotNull();
    assertThat(response.orderId()).isEqualTo(orderId);
    assertThat(response.productId()).isNull();
    assertThat(response.productName()).isNull();
    assertThat(response.status()).isEqualTo(ReviewStatus.PENDING);
  }

  @Test
  void adminReviewListReturnsAnEmptyPageWhenThereAreNoReviews() {
    doReturn(0L).when(jdbc).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    doReturn(List.of()).when(jdbc).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class));
    OrderReviewService service = new OrderReviewService(orders, jdbc, new ObjectMapper());

    ReviewPageResponse<OrderReviewResponse> page = service.adminList(ReviewStatus.PENDING, 0, 20, false);

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isZero();
    assertThat(page.totalPages()).isZero();
    assertThat(page.number()).isZero();
  }
}
