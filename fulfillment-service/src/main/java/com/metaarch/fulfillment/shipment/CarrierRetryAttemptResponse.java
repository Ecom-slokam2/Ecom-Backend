package com.metaarch.fulfillment.shipment;

import java.time.Instant;
import java.util.UUID;

public record CarrierRetryAttemptResponse(UUID attemptId, int attemptNumber, CarrierRetryOutcome outcome,
                                          Integer carrierStatus, String errorCode, Instant startedAt,
                                          Instant completedAt) {
  static CarrierRetryAttemptResponse from(CarrierRetryAttempt attempt) {
    return new CarrierRetryAttemptResponse(attempt.getId(), attempt.getAttemptNumber(), attempt.getOutcome(),
        attempt.getCarrierStatus(), attempt.getErrorCode(), attempt.getStartedAt(), attempt.getCompletedAt());
  }
}
