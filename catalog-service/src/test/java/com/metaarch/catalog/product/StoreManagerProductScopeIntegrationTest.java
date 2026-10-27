package com.metaarch.catalog.product;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.metaarch.catalog.category.Category;
import com.metaarch.catalog.category.CategoryRepository;
import com.metaarch.catalog.banner.Banner;
import com.metaarch.catalog.banner.BannerRepository;
import com.metaarch.catalog.banner.BannerScope;
import com.metaarch.catalog.coupon.CouponRequest;
import com.metaarch.catalog.coupon.CouponService;
import com.metaarch.catalog.storeproduct.StoreProduct;
import com.metaarch.catalog.storeproduct.StoreProductRepository;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.jwt.secret=local-isolated-test-secret-that-is-long-enough-for-hmac-sha256")
@AutoConfigureMockMvc
class StoreManagerProductScopeIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired CategoryRepository categories;
  @Autowired ProductRepository products;
  @Autowired StoreProductRepository mappings;
  @Autowired BannerRepository banners;
  @Autowired CouponService coupons;

  @Test
  void productListsAndDetailsAreLimitedToTheAssignedStore() throws Exception {
    String suffix = UUID.randomUUID().toString();
    Category category = categories.save(new Category("QA " + suffix, "qa-" + suffix));
    Product assigned = products.save(new Product(category, "Assigned " + suffix, "assigned-" + suffix, "ASSIGNED-" + suffix));
    Product other = products.save(new Product(category, "Other " + suffix, "other-" + suffix, "OTHER-" + suffix));
    StoreProduct assignedMapping = new StoreProduct("store-qa-assigned", assigned);
    assignedMapping.setAvailable(true);
    mappings.save(assignedMapping);
    mappings.save(new StoreProduct("store-qa-other", other));
    var manager = jwt().authorities(() -> "ROLE_STORE_MANAGER", () -> "SCOPE_STORE:store-qa-assigned");

    mvc.perform(get("/api/v1/products").with(manager).param("storeId", "store-qa-assigned").param("search", suffix))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(assigned.getId().toString()))
        .andExpect(jsonPath("$.content[0].locationAvailable").value(true));

    mvc.perform(get("/api/v1/products").with(manager).param("storeId", "store-qa-other").param("search", suffix))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/products").with(manager).param("search", suffix))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/products/{id}", assigned.getId()).with(manager))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/products/{id}", other.getId()).with(manager))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/categories/{id}/products", category.getId()).with(manager)
            .param("storeId", "store-qa-assigned").param("search", suffix))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(assigned.getId().toString()));
    mvc.perform(get("/api/v1/categories/{id}/products", category.getId()).with(manager)
            .param("storeId", "store-qa-other").param("search", suffix))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/products").param("search", suffix).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  void storeManagerCatalogCollectionsDoNotReturnUnscopedBannerOrCouponRecords() throws Exception {
    String suffix = UUID.randomUUID().toString();
    Banner globalBanner = new Banner("Public " + suffix, "https://example.test/banner.png");
    globalBanner.setScope(BannerScope.GLOBAL);
    banners.save(globalBanner);

    var otherStoreCoupon = coupons.create(new CouponRequest(
        "QA-" + suffix.substring(0, 8), "Other store " + suffix, null, "PERCENTAGE", new BigDecimal("10.00"),
        BigDecimal.ZERO, null, LocalDate.now(), LocalDate.now().plusDays(1), 10, null, "STORE", null,
        "store-qa-other", null, null, "All customers", "ACTIVE"));

    var manager = jwt().authorities(() -> "ROLE_STORE_MANAGER", () -> "SCOPE_STORE:store-qa-assigned");
    mvc.perform(get("/api/v1/banners").with(manager).param("all", "true"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='" + globalBanner.getId() + "')]").doesNotExist());
    mvc.perform(get("/api/v1/coupons").with(manager))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='" + otherStoreCoupon.id() + "')]").doesNotExist());
    mvc.perform(get("/api/v1/banners").param("all", "true"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='" + globalBanner.getId() + "')]").exists());
    mvc.perform(get("/api/v1/coupons"))
        .andExpect(status().isUnauthorized());
  }
}
