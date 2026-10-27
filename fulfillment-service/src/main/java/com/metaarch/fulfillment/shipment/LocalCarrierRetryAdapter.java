package com.metaarch.fulfillment.shipment;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** A deterministic QA adapter. It accepts submissions in-process and never contacts a carrier. */
@Component
@Profile("carrier-retry-qa")
public class LocalCarrierRetryAdapter implements CarrierRetryAdapter {
  private final Set<String> acceptedKeys = ConcurrentHashMap.newKeySet();

  @Override public boolean supportsIdempotencyKey() { return true; }

  @Override
  public CarrierSubmissionResult submit(CarrierRetrySubmission shipment, AssignDeliveryPartnerRequest partner, String idempotencyKey) {
    acceptedKeys.add(shipment.shipmentId() + ":" + idempotencyKey);
    return new CarrierSubmissionResult(202);
  }

  public int acceptedSubmissionCount() { return acceptedKeys.size(); }
}
