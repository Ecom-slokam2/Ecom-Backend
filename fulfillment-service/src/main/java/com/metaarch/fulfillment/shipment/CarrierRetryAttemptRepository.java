package com.metaarch.fulfillment.shipment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarrierRetryAttemptRepository extends JpaRepository<CarrierRetryAttempt, UUID> {
  List<CarrierRetryAttempt> findAllByShipmentIdOrderByAttemptNumber(UUID shipmentId);
  List<CarrierRetryAttempt> findAllByShipmentIdAndIdempotencyKeyOrderByAttemptNumber(UUID shipmentId, String idempotencyKey);
  Optional<CarrierRetryAttempt> findTopByShipmentIdOrderByAttemptNumberDesc(UUID shipmentId);
  long countByShipmentId(UUID shipmentId);
}
