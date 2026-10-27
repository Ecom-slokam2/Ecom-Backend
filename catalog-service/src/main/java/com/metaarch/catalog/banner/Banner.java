package com.metaarch.catalog.banner;

import java.time.LocalDate;
import java.util.UUID;
import com.metaarch.catalog.common.AuditedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "banners")
public class Banner extends AuditedEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(nullable = false, length = 180) private String title;
  @Column(length = 1000) private String description;
  // Stores either an HTTP(S) URL or an inline data: URI, so it must accept large values.
  @Column(name = "image_url", nullable = false, columnDefinition = "MEDIUMTEXT") private String imageUrl;
  @Column(name = "mobile_image_url", columnDefinition = "MEDIUMTEXT") private String mobileImageUrl;
  @Column(name = "target_url", length = 2048) private String targetUrl;
  @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20) private BannerScope scope = BannerScope.GLOBAL;
  @Column(name = "location_id", length = 100) private String locationId;
  @Column(name = "store_id", length = 100) private String storeId;
  @Column(name = "sort_order", nullable = false) private int sortOrder = 0;
  @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20) private BannerStatus status = BannerStatus.ACTIVE;
  @Column(name = "starts_on") private LocalDate startsOn;
  @Column(name = "ends_on") private LocalDate endsOn;

  protected Banner() {}
  public Banner(String title, String imageUrl) { this.title = title; this.imageUrl = imageUrl; }

  public UUID getId() { return id; }
  public String getTitle() { return title; } public void setTitle(String v) { title = v; }
  public String getDescription() { return description; } public void setDescription(String v) { description = v; }
  public String getImageUrl() { return imageUrl; } public void setImageUrl(String v) { imageUrl = v; }
  public String getMobileImageUrl() { return mobileImageUrl; } public void setMobileImageUrl(String v) { mobileImageUrl = v; }
  public String getTargetUrl() { return targetUrl; } public void setTargetUrl(String v) { targetUrl = v; }
  public BannerScope getScope() { return scope; } public void setScope(BannerScope v) { scope = v; }
  public String getLocationId() { return locationId; } public void setLocationId(String v) { locationId = v; }
  public String getStoreId() { return storeId; } public void setStoreId(String v) { storeId = v; }
  public int getSortOrder() { return sortOrder; } public void setSortOrder(int v) { sortOrder = v; }
  public BannerStatus getStatus() { return status; } public void setStatus(BannerStatus v) { status = v; }
  public LocalDate getStartsOn() { return startsOn; } public void setStartsOn(LocalDate v) { startsOn = v; }
  public LocalDate getEndsOn() { return endsOn; } public void setEndsOn(LocalDate v) { endsOn = v; }
}
