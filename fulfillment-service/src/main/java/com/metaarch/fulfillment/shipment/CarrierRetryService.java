package com.metaarch.fulfillment.shipment;

import com.metaarch.fulfillment.event.FulfillmentEventPublisher;
import com.metaarch.fulfillment.event.FulfillmentEventType;
import com.metaarch.fulfillment.exception.InvalidFulfillmentStateException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CarrierRetryService {
  public static final int MAX_ATTEMPTS = 3;
  private final ShipmentRepository shipments;
  private final CarrierRetryAttemptRepository attempts;
  private final CarrierRetryAdapter adapter;
  private final CarrierRetryDelay delay;
  private final FulfillmentEventPublisher events;
  private final TransactionTemplate transaction;

  public CarrierRetryService(ShipmentRepository shipments, CarrierRetryAttemptRepository attempts, CarrierRetryAdapter adapter,
                             CarrierRetryDelay delay, FulfillmentEventPublisher events, PlatformTransactionManager transactions) {
    this.shipments = shipments;
    this.attempts = attempts;
    this.adapter = adapter;
    this.delay = delay;
    this.events = events;
    this.transaction = new TransactionTemplate(transactions);
    this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Each attempt is committed as PENDING before the adapter call. If the process or connection dies after the carrier
   * accepted a request, a later call sees PENDING and returns UNKNOWN instead of submitting a duplicate operation.
   */
  public CarrierRetryResponse retry(UUID shipmentId, AssignDeliveryPartnerRequest partner, String idempotencyKey) {
    List<CarrierRetryAttemptResponse> operationHistory = new ArrayList<>();
    CarrierRetryOutcome result = CarrierRetryOutcome.RETRYABLE_FAILURE;
    int totalAttempts = 0;

    while (true) {
      BeginResult begin = transaction.execute(status -> beginAttempt(shipmentId, idempotencyKey));
      if (begin == null) throw new IllegalStateException("Carrier retry transaction returned no result");
      totalAttempts = begin.totalAttempts();
      if (begin.terminalOutcome() != null) {
        operationHistory = begin.history();
        result = begin.terminalOutcome();
        break;
      }
      CarrierRetryOutcome attemptOutcome;
      Integer carrierStatus = null;
      String errorCode = null;
      boolean retryable = false;
      try {
        if (!adapter.supportsIdempotencyKey()) {
          attemptOutcome = CarrierRetryOutcome.PERMANENT_FAILURE;
          errorCode = "IDEMPOTENCY_NOT_SUPPORTED";
        } else {
          CarrierSubmissionResult submission = adapter.submit(begin.submission(), partner, idempotencyKey);
          carrierStatus = submission.statusCode();
          if (carrierStatus >= 200 && carrierStatus < 300) {
            attemptOutcome = CarrierRetryOutcome.SUCCEEDED;
          } else {
            retryable = isRetryableStatus(carrierStatus);
            attemptOutcome = retryable ? CarrierRetryOutcome.RETRYABLE_FAILURE : CarrierRetryOutcome.PERMANENT_FAILURE;
            errorCode = "CARRIER_HTTP_" + carrierStatus;
          }
        }
      } catch (CarrierRetryException failure) {
        carrierStatus = failure.statusCode();
        errorCode = failure.errorCode();
        if (failure.ambiguous()) {
          attemptOutcome = CarrierRetryOutcome.UNKNOWN;
        } else {
          retryable = failure.statusCode() == null || isRetryableStatus(failure.statusCode());
          attemptOutcome = retryable ? CarrierRetryOutcome.RETRYABLE_FAILURE : CarrierRetryOutcome.PERMANENT_FAILURE;
        }
      } catch (RuntimeException failure) {
        attemptOutcome = CarrierRetryOutcome.UNKNOWN;
        errorCode = "CARRIER_OUTCOME_UNKNOWN";
      }

      CarrierRetryOutcome completedOutcome = attemptOutcome;
      Integer completedCarrierStatus = carrierStatus;
      String completedErrorCode = errorCode;
      boolean completedRetryable = retryable;
      FinishResult finished = transaction.execute(status -> finishAttempt(begin.attemptId(), partner, completedOutcome,
          completedCarrierStatus, completedErrorCode, completedRetryable));
      if (finished == null) throw new IllegalStateException("Carrier retry result transaction returned no result");
      operationHistory.add(finished.attempt());
      totalAttempts = finished.totalAttempts();
      result = finished.outcome();
      if (result != CarrierRetryOutcome.RETRYABLE_FAILURE) break;
      if (totalAttempts >= MAX_ATTEMPTS) {
        result = CarrierRetryOutcome.EXHAUSTED;
        break;
      }
      try {
        delay.beforeAttempt(totalAttempts + 1);
      } catch (CarrierRetryException interrupted) {
        // The previous carrier result is known and persisted; an interrupted backoff did not submit another call.
        break;
      }
    }
    return new CarrierRetryResponse(shipmentId, result, totalAttempts, MAX_ATTEMPTS, List.copyOf(operationHistory));
  }

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public List<CarrierRetryAttemptResponse> history(UUID shipmentId) {
    return attempts.findAllByShipmentIdOrderByAttemptNumber(shipmentId).stream().map(CarrierRetryAttemptResponse::from).toList();
  }

  private BeginResult beginAttempt(UUID shipmentId, String idempotencyKey) {
    Shipment shipment = shipments.findForUpdate(shipmentId)
        .orElseThrow(() -> new com.metaarch.fulfillment.exception.ResourceNotFoundException("Shipment not found"));
    List<CarrierRetryAttempt> sameKey = attempts.findAllByShipmentIdAndIdempotencyKeyOrderByAttemptNumber(shipmentId, idempotencyKey);
    long count = attempts.countByShipmentId(shipmentId);
    CarrierRetryAttempt latest = attempts.findTopByShipmentIdOrderByAttemptNumberDesc(shipmentId).orElse(null);

    if (!sameKey.isEmpty()) {
      CarrierRetryAttempt lastForKey = sameKey.get(sameKey.size() - 1);
      if (lastForKey.getOutcome() != CarrierRetryOutcome.RETRYABLE_FAILURE) {
        return terminal(shipmentId, sameKey, lastForKey.getOutcome(), count);
      }
      if (count >= MAX_ATTEMPTS) return terminal(shipmentId, sameKey, CarrierRetryOutcome.EXHAUSTED, count);
    }
    if (latest != null && latest.getOutcome() == CarrierRetryOutcome.PENDING) {
      return terminal(shipmentId, sameKey, CarrierRetryOutcome.UNKNOWN, count);
    }
    if (latest != null && (latest.getOutcome() == CarrierRetryOutcome.UNKNOWN
        || latest.getOutcome() == CarrierRetryOutcome.PERMANENT_FAILURE
        || latest.getOutcome() == CarrierRetryOutcome.SUCCEEDED)) {
      return terminal(shipmentId, sameKey.isEmpty() ? List.of(latest) : sameKey, latest.getOutcome(), count);
    }
    if (shipment.getStatus() != ShipmentStatus.FAILED) {
      if (latest != null && latest.getOutcome() == CarrierRetryOutcome.SUCCEEDED)
        return terminal(shipmentId, sameKey, CarrierRetryOutcome.SUCCEEDED, count);
      throw new InvalidFulfillmentStateException("Only failed shipments can be retried");
    }
    if (count >= MAX_ATTEMPTS) return terminal(shipmentId, sameKey, CarrierRetryOutcome.EXHAUSTED, count);

    int attemptNumber = (int) count + 1;
    CarrierRetryAttempt attempt = attempts.saveAndFlush(new CarrierRetryAttempt(shipmentId, idempotencyKey, attemptNumber));
    CarrierRetrySubmission submission = new CarrierRetrySubmission(shipment.getId(), shipment.getOrderId(),
        shipment.getFulfillment().getStoreId(), shipment.getCarrier(), shipment.getTrackingNumber());
    return new BeginResult(attempt.getId(), submission, attemptNumber, null, (int) count + 1, List.of());
  }

  private FinishResult finishAttempt(UUID attemptId, AssignDeliveryPartnerRequest partner, CarrierRetryOutcome outcome,
                                     Integer carrierStatus, String errorCode, boolean retryable) {
    CarrierRetryAttempt attempt = attempts.findById(attemptId)
        .orElseThrow(() -> new com.metaarch.fulfillment.exception.ResourceNotFoundException("Carrier retry attempt not found"));
    Shipment shipment = shipments.findForUpdate(attempt.getShipmentId())
        .orElseThrow(() -> new com.metaarch.fulfillment.exception.ResourceNotFoundException("Shipment not found"));
    if (outcome == CarrierRetryOutcome.SUCCEEDED && shipment.getStatus() != ShipmentStatus.FAILED) {
      outcome = CarrierRetryOutcome.UNKNOWN;
      errorCode = "SHIPMENT_CHANGED_DURING_RETRY";
      retryable = false;
    }
    attempt.complete(outcome, carrierStatus, errorCode, retryable);
    if (outcome == CarrierRetryOutcome.SUCCEEDED) {
      shipment.assignDeliveryPartner(partner.deliveryPartnerId().trim(), partner.deliveryPartnerName().trim(),
          partner.deliveryPartnerPhone() == null ? null : partner.deliveryPartnerPhone().trim(),
          partner.vehicleNumber() == null ? null : partner.vehicleNumber().trim());
      shipment.setStatus(ShipmentStatus.CREATED);
      events.publish(FulfillmentEventType.SHIPMENT_STATUS_UPDATED, shipment.getFulfillment(), shipment, null);
    }
    return new FinishResult(CarrierRetryAttemptResponse.from(attempt), outcome, (int) attempts.countByShipmentId(attempt.getShipmentId()));
  }

  private BeginResult terminal(UUID shipmentId, List<CarrierRetryAttempt> rows, CarrierRetryOutcome outcome, long count) {
    return new BeginResult(null, null, 0, outcome, (int) count,
        rows.stream().map(CarrierRetryAttemptResponse::from).toList());
  }

  private static boolean isRetryableStatus(int status) { return status == 408 || status == 429 || status >= 500 && status <= 599; }

  private record BeginResult(UUID attemptId, CarrierRetrySubmission submission, int retryNumber,
                             CarrierRetryOutcome terminalOutcome, int totalAttempts,
                             List<CarrierRetryAttemptResponse> history) { }
  private record FinishResult(CarrierRetryAttemptResponse attempt, CarrierRetryOutcome outcome, int totalAttempts) { }
}
