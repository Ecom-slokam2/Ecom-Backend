package com.metaarch.catalog.product;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.jwt.secret=local-isolated-test-secret-that-is-long-enough-for-hmac-sha256")
@AutoConfigureMockMvc
@Transactional
class ProductGlobalCatalogAuthorizationIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void globalCatalogWritesRemainUnavailableToLocationAdmin() throws Exception {
    String categoryBody = """
        {"name":"LA11 disposable category","slug":"la11-disposable-category","active":true}
        """;
    String categoryResponse = mvc.perform(post("/api/v1/categories").with(globalAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(categoryBody))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String categoryId = json.readTree(categoryResponse).get("id").asText();

    String productBody = product(categoryId, "LA11 disposable product", "LA11-DISPOSABLE", "ACTIVE");
    mvc.perform(post("/api/v1/products").with(locationAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(productBody))
        .andExpect(status().isForbidden());

    String productResponse = mvc.perform(post("/api/v1/products").with(globalAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(productBody))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    JsonNode created = json.readTree(productResponse);
    String productId = created.get("id").asText();

    mvc.perform(put("/api/v1/products/{id}", productId).with(locationAdmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(product(categoryId, "LA11 disposable product", "LA11-DISPOSABLE", "INACTIVE")))
        .andExpect(status().isForbidden());

    mvc.perform(put("/api/v1/products/{id}", productId).with(globalAdmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(product(categoryId, "LA11 disposable product", "LA11-DISPOSABLE", "DRAFT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"));
  }

  @Test
  void productVariantsAndImagesRemainGlobalCatalogWrites() throws Exception {
    String categoryResponse = mvc.perform(post("/api/v1/categories").with(globalAdmin())
        .contentType(MediaType.APPLICATION_JSON).content("""
          {"name":"LA13 disposable category","slug":"la13-disposable-category","active":true}
          """))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String categoryId = json.readTree(categoryResponse).get("id").asText();
    String productResponse = mvc.perform(post("/api/v1/products").with(globalAdmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(product(categoryId, "LA13 disposable product", "LA13-DISPOSABLE", "ACTIVE")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String productId = json.readTree(productResponse).get("id").asText();
    String variant = """
        {"sku":"LA13-VARIANT","name":"Disposable variant","attributes":"{}","price":10.00,"compareAtPrice":12.00,"active":true}
        """;
    String image = """
        {"url":"https://example.invalid/la13-qa.png","altText":"Disposable QA image","displayOrder":0,"primaryImage":true}
        """;

    mvc.perform(post("/api/v1/products/{id}/variants", productId).contentType(MediaType.APPLICATION_JSON).content(variant))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/products/{id}/images", productId).contentType(MediaType.APPLICATION_JSON).content(image))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/products/{id}/variants", productId).with(locationAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(variant)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/products/{id}/images", productId).with(locationAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(image)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/products/{id}/variants", productId).with(globalAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(variant)).andExpect(status().isCreated());
    mvc.perform(post("/api/v1/products/{id}/images", productId).with(globalAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(image)).andExpect(status().isCreated());
  }

  private String product(String categoryId, String name, String sku, String status) {
    return """
        {"categoryId":"%s","name":"%s","slug":"la11-disposable-product","sku":"%s","description":"Isolated regression fixture","status":"%s"}
        """.formatted(categoryId, name, sku, status);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor locationAdmin() {
    return jwt().authorities(() -> "ROLE_LOCATION_ADMIN", () -> "SCOPE_LOCATION:Hyderabad");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor globalAdmin() {
    return jwt().authorities(() -> "ROLE_GLOBAL_ADMIN");
  }
}
