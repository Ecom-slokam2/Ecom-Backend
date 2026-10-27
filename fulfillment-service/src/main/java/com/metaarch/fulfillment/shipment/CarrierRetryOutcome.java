package com.metaarch.fulfillment.shipment;

public enum CarrierRetryOutcome {
  PENDING, SUCCEEDED, RETRYABLE_FAILURE, PERMANENT_FAILURE, UNKNOWN, EXHAUSTED
}
