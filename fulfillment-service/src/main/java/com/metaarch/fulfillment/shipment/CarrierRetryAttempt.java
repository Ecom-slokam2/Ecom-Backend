package com.metaarch.fulfillment.shipment;

import com.metaarch.fulfillment.common.AuditedEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "carrier_retry_attempts", uniqueConstraints = {
    @UniqueConstraint(name = "uk_carrier_retry_shipment_attempt", columnNames = {"shipment_id", "attempt_number"}),
    @UniqueConstraint(name = "uk_carrier_retry_request_attempt", columnNames = {"shipment_id", "idempotency_key", "attempt_number"})
})
public class CarrierRetryAttempt extends AuditedEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "shipment_id", nullable = false) private UUID shipmentId;
  @Column(name = "idempotency_key", nullable = false, length = 100) private String idempotencyKey;
  @Column(name = "attempt_number", nullable = false) private int attemptNumber;
  @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 30) private CarrierRetryOutcome outcome;
  @Column(name = "carrier_status") private Integer carrierStatus;
  @Column(name = "error_code", length = 80) private String errorCode;
  @Column(name = "retryable", nullable = false) private boolean retryable;
  @Column(name = "started_at", nullable = false) private Instant startedAt;
  @Column(name = "completed_at") private Instant completedAt;

  protected CarrierRetryAttempt() { }
  CarrierRetryAttempt(UUID shipmentId, String idempotencyKey, int attemptNumber) {
    this.shipmentId = shipmentId;
    this.idempotencyKey = idempotencyKey;
    this.attemptNumber = attemptNumber;
    this.outcome = CarrierRetryOutcome.UNKNOWN;
    this.startedAt = Instant.now();
  }
  public UUID getId() { return id; }
  public UUID getShipmentId() { return shipmentId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public int getAttemptNumber() { return attemptNumber; }
  public CarrierRetryOutcome getOutcome() { return outcome; }
  public Integer getCarrierStatus() { return carrierStatus; }
  public String getErrorCode() { return errorCode; }
  public boolean isRetryable() { return retryable; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getCompletedAt() { return completedAt; }
  void complete(CarrierRetryOutcome outcome, Integer carrierStatus, String errorCode, boolean retryable) {
    this.outcome = outcome;
    this.carrierStatus = carrierStatus;
    this.errorCode = errorCode;
    this.retryable = retryable;
    this.completedAt = Instant.now();
  }
}
