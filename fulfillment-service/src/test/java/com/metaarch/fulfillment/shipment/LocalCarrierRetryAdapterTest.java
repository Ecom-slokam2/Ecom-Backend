package com.metaarch.fulfillment.shipment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalCarrierRetryAdapterTest {
  @Test
  void qaAdapterIsLocalAndIdempotent() {
    LocalCarrierRetryAdapter adapter = new LocalCarrierRetryAdapter();
    assertThat(adapter.supportsIdempotencyKey()).isTrue();
    CarrierRetrySubmission submission = new CarrierRetrySubmission(UUID.randomUUID(), UUID.randomUUID(), "qa-store", "Local QA", "LOCAL-QA");
    assertThat(adapter.submit(submission, new AssignDeliveryPartnerRequest("qa-partner", "QA Partner", null, null), "qa-key").statusCode()).isEqualTo(202);
    assertThat(adapter.submit(submission, new AssignDeliveryPartnerRequest("qa-partner", "QA Partner", null, null), "qa-key").statusCode()).isEqualTo(202);
    assertThat(adapter.acceptedSubmissionCount()).isEqualTo(1);
  }

  @Test
  void normalRuntimeAdapterFailsClosedWithoutSendingAnything() {
    CarrierRetryAdapter disabled = new CarrierRetryAdapterConfiguration().disabledCarrierRetryAdapter();
    assertThat(disabled.supportsIdempotencyKey()).isFalse();
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> disabled.submit(null, null, "qa-key"))
        .isInstanceOf(CarrierRetryException.class)
        .extracting(error -> ((CarrierRetryException) error).errorCode())
        .isEqualTo("CARRIER_NOT_CONFIGURED");
  }
}
