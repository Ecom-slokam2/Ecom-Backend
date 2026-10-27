package com.metaarch.notification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.metaarch.notification.exception.InvalidNotificationStateException;
import com.metaarch.notification.exception.ResourceNotFoundException;
import com.metaarch.notification.sender.NotificationSender;
import com.metaarch.notification.template.NotificationTemplateService;
import com.metaarch.notification.template.TemplateRenderer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class NotificationServiceTest {
  private final NotificationRepository repository = mock(NotificationRepository.class);
  private final NotificationTemplateService templateService = mock(NotificationTemplateService.class);
  private final TemplateRenderer renderer = mock(TemplateRenderer.class);
  private final NotificationSender sender = mock(NotificationSender.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private NotificationService service;

  @BeforeEach
  void setUp() {
    when(sender.channel()).thenReturn(NotificationChannel.EMAIL);
    service = new NotificationService(repository, templateService, renderer, List.of(sender), true, meterRegistry);
    when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void createsAndReadsNotification() {
    UUID customerId = UUID.randomUUID();
    NotificationResponse response = service.create(new CreateNotificationRequest(customerId, null,
        NotificationType.ORDER_CREATED, NotificationChannel.EMAIL, "a@example.com", "Subject", "Message"));

    assertThat(response.customerId()).isEqualTo(customerId);
    assertThat(response.status()).isEqualTo(NotificationStatus.PENDING);
    Notification saved = new Notification(customerId, null, NotificationType.ORDER_CREATED, NotificationChannel.EMAIL,
        "a@example.com", "Subject", "Message", null);
    UUID notificationId = UUID.randomUUID();
    when(repository.findById(notificationId)).thenReturn(Optional.of(saved));
    assertThat(service.get(notificationId)).isNotNull();
  }

  @Test
  void reportsMissingNotification() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void queriesCustomerOrderAndHistoryPages() {
    PageRequest page = PageRequest.of(0, 10);
    when(repository.findByCustomerIdAndClearedAtIsNull(any(), any())).thenReturn(new PageImpl<>(List.of()));
    when(repository.findByOrderId(any(), any())).thenReturn(new PageImpl<>(List.of()));
    when(repository.findAll(page)).thenReturn(new PageImpl<>(List.of()));

    assertThat(service.getByCustomer(UUID.randomUUID(), page).content()).isEmpty();
    assertThat(service.getByOrder(UUID.randomUUID(), page).content()).isEmpty();
    assertThat(service.history(page).content()).isEmpty();
    verify(repository).findAll(page);
  }

  @Test
  void supportsTheValidStatusTransitions() {
    for (NotificationStatus status : List.of(NotificationStatus.SENT, NotificationStatus.FAILED, NotificationStatus.CANCELLED)) {
      Notification notification = pending();
      when(repository.findById(any())).thenReturn(Optional.of(notification));

      NotificationResponse response = service.updateStatus(notification.getId(), new UpdateNotificationStatusRequest(status));

      assertThat(response.status()).isEqualTo(status);
    }
  }

  @Test
  void rejectsUnsupportedAndNonPendingStatusTransitions() {
    Notification notification = pending();
    when(repository.findById(any())).thenReturn(Optional.of(notification));
    assertThatThrownBy(() -> service.updateStatus(notification.getId(), new UpdateNotificationStatusRequest(NotificationStatus.PENDING)))
        .isInstanceOf(InvalidNotificationStateException.class);

    notification.markSent(java.time.Instant.now());
    assertThatThrownBy(() -> service.updateStatus(notification.getId(), new UpdateNotificationStatusRequest(NotificationStatus.FAILED)))
        .isInstanceOf(InvalidNotificationStateException.class);
  }

  @Test
  void sendsSuccessfullyAndRecordsSenderFailures() {
    Notification notification = pending();
    when(repository.findById(notification.getId())).thenReturn(Optional.of(notification));
    assertThat(service.send(notification.getId(), null).status()).isEqualTo(NotificationStatus.SENT);
    verify(sender).send(notification);

    Notification failed = pending();
    when(repository.findById(failed.getId())).thenReturn(Optional.of(failed));
    doThrow(new IllegalStateException("mail unavailable")).when(sender).send(failed);
    assertThat(service.send(failed.getId(), null).status()).isEqualTo(NotificationStatus.FAILED);
    assertThat(failed.getFailureReason()).isEqualTo("mail unavailable");
  }

  @Test
  void supportsSimulatedFailuresAndDisabledSending() {
    Notification simulated = pending();
    when(repository.findById(simulated.getId())).thenReturn(Optional.of(simulated));
    assertThat(service.send(simulated.getId(), new SendNotificationRequest(true, "test failure")).status())
        .isEqualTo(NotificationStatus.FAILED);

    Notification disabled = pending();
    NotificationService disabledService = new NotificationService(repository, templateService, renderer, List.of(sender), false, meterRegistry);
    when(repository.findById(disabled.getId())).thenReturn(Optional.of(disabled));
    assertThat(disabledService.send(disabled.getId(), null).status()).isEqualTo(NotificationStatus.PENDING);
  }

  @Test
  void reusesExistingEventsAndRendersNewEvents() {
    UUID eventId = UUID.randomUUID();
    Notification existing = pending();
    when(repository.findBySourceEventId(eventId)).thenReturn(Optional.of(existing));
    assertThat(service.createFromEvent(eventId, NotificationType.ORDER_CREATED, NotificationChannel.EMAIL,
        UUID.randomUUID(), null, "a@example.com", Map.of()).status()).isEqualTo(NotificationStatus.PENDING);

    when(repository.findBySourceEventId(eventId)).thenReturn(Optional.empty());
    when(templateService.findActive(NotificationType.ORDER_CREATED, NotificationChannel.EMAIL)).thenReturn(Optional.empty());
    NotificationResponse response = service.createFromEvent(eventId, NotificationType.ORDER_CREATED, NotificationChannel.EMAIL,
        UUID.randomUUID(), UUID.randomUUID(), "a@example.com", Map.of("orderId", "order-1"));
    assertThat(response.subject()).startsWith("E-commerce update:");
    assertThat(response.status()).isEqualTo(NotificationStatus.SENT);
  }

  private static Notification pending() {
    return new Notification(UUID.randomUUID(), UUID.randomUUID(), NotificationType.ORDER_CREATED,
        NotificationChannel.EMAIL, "a@example.com", "Subject", "Message", null);
  }
}
