package com.metaarch.fulfillment.shipment;

public interface CarrierRetryAdapter {
  boolean supportsIdempotencyKey();
  CarrierSubmissionResult submit(CarrierRetrySubmission shipment, AssignDeliveryPartnerRequest partner, String idempotencyKey) throws CarrierRetryException;
}
