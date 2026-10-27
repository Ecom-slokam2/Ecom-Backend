package com.metaarch.order.order;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.metaarch.order.event.*;
import com.metaarch.order.exception.InvalidOrderStateException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class VerifiedDeliveryTest {
  private final OrderRepository repository = mock(OrderRepository.class);
  private final OrderEventPublisher publisher = mock(OrderEventPublisher.class);
  private final OrderService service = new OrderService(repository, mock(OrderItemRepository.class), publisher,
      mock(CatalogDeliveryQuoteClient.class), mock(GlobalStoreAssignmentCatalogClient.class),
      mock(GlobalStoreAssignmentInventoryClient.class), mock(FulfillmentLifecycleVerifier.class));
  private final UUID id = UUID.randomUUID();
  private final Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), "store-a", "test-order");
  private FulfillmentEvent event(String status, UUID customer, String store) {
    return new FulfillmentEvent(UUID.randomUUID(), "SHIPMENT_DELIVERED", UUID.randomUUID(), id, customer, store,
        "DELIVERED", UUID.randomUUID(), status, Instant.now());
  }
  private void persisted() { when(repository.findLockedById(id)).thenReturn(Optional.of(order)); }

  @Test void verifiedDeliveryRepairsMissedIntermediateStatusesAndIsIdempotent() {
    persisted();
    var event = event("DELIVERED", order.getCustomerId(), order.getStoreId());
    service.applyVerifiedDelivery(event);
    service.applyVerifiedDelivery(event);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(publisher, times(1)).publish(OrderEventType.ORDER_STATUS_UPDATED, order);
  }
  @Test void foreignCustomerAndStoreCannotAuthorizeDelivery() {
    persisted();
    assertThatThrownBy(() -> service.applyVerifiedDelivery(event("DELIVERED", UUID.randomUUID(), "store-a"))).isInstanceOf(InvalidOrderStateException.class);
    assertThatThrownBy(() -> service.applyVerifiedDelivery(event("DELIVERED", order.getCustomerId(), "store-b"))).isInstanceOf(InvalidOrderStateException.class);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    verifyNoInteractions(publisher);
  }
  @Test void nonDeliveredAndIncompleteEventsDoNotAuthorizeReviews() {
    assertThatThrownBy(() -> service.applyVerifiedDelivery(event("IN_TRANSIT", order.getCustomerId(), "store-a"))).isInstanceOf(InvalidOrderStateException.class);
    var incomplete = new FulfillmentEvent(UUID.randomUUID(), "SHIPMENT_DELIVERED", null, id, order.getCustomerId(), "store-a", "DELIVERED", null, "DELIVERED", Instant.now());
    assertThatThrownBy(() -> service.applyVerifiedDelivery(incomplete)).isInstanceOf(InvalidOrderStateException.class);
    verifyNoInteractions(repository, publisher);
  }
  @Test void cancelledOrdersCannotBecomeReviewEligible() {
    persisted();order.setStatus(OrderStatus.CANCELLED);
    assertThatThrownBy(() -> service.applyVerifiedDelivery(event("DELIVERED", order.getCustomerId(), "store-a"))).isInstanceOf(InvalidOrderStateException.class);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    verifyNoInteractions(publisher);
  }
  @Test void consumerOnlyAcceptsDeliveryEvidenceFromItsRegisteredSource() {
    var mockService = mock(OrderService.class);
    var consumer = new FulfillmentEventConsumer(mockService);
    assertThat(consumer.kind()).isEqualTo("fulfillment");
    assertThat(consumer.source()).isEqualTo("fulfillment-service");
    var mapper = new ObjectMapper().findAndRegisterModules();
    var delivered = event("DELIVERED", order.getCustomerId(), "store-a");
    consumer.handle(mapper.valueToTree(delivered));
    verify(mockService).applyVerifiedDelivery(delivered);
    var tracking = mapper.valueToTree(delivered);
    ((com.fasterxml.jackson.databind.node.ObjectNode) tracking).put("eventType", "TRACKING_EVENT_ADDED");
    consumer.handle(tracking);
    verifyNoMoreInteractions(mockService);
  }
}
