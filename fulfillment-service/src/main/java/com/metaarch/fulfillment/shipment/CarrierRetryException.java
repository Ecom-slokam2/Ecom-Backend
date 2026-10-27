package com.metaarch.fulfillment.shipment;

public class CarrierRetryException extends RuntimeException {
  private final Integer statusCode;
  private final String errorCode;
  private final boolean ambiguous;
  public CarrierRetryException(Integer statusCode, String errorCode, boolean ambiguous) {
    super(errorCode);
    this.statusCode = statusCode;
    this.errorCode = sanitize(errorCode);
    this.ambiguous = ambiguous;
  }
  public Integer statusCode() { return statusCode; }
  public String errorCode() { return errorCode; }
  public boolean ambiguous() { return ambiguous; }
  private static String sanitize(String code) {
    if (code == null || !code.matches("[A-Za-z0-9_.-]{1,80}")) return "CARRIER_ERROR";
    return code;
  }
}
