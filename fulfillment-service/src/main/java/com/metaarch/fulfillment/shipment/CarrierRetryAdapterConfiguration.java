package com.metaarch.fulfillment.shipment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CarrierRetryAdapterConfiguration {
  @Bean
  @ConditionalOnMissingBean(CarrierRetryAdapter.class)
  CarrierRetryAdapter disabledCarrierRetryAdapter() {
    return new CarrierRetryAdapter() {
      @Override public boolean supportsIdempotencyKey() { return false; }
      @Override public CarrierSubmissionResult submit(CarrierRetrySubmission shipment, AssignDeliveryPartnerRequest partner, String idempotencyKey) {
        throw new CarrierRetryException(null, "CARRIER_NOT_CONFIGURED", false);
      }
    };
  }
}
