package com.metaarch.fulfillment.shipment;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class CarrierRetryDelay {
  public void beforeAttempt(int attemptNumber) {
    long baseMillis = attemptNumber == 2 ? 1_000 : 2_000;
    try {
      Thread.sleep(baseMillis + ThreadLocalRandom.current().nextLong(0, 251));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new CarrierRetryException(null, "RETRY_INTERRUPTED", true);
    }
  }
}
