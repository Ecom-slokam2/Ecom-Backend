package com.metaarch.order.order;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CatalogItemPricingTest {
  private final UUID product = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private final UUID variant = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final CatalogDeliveryQuoteClient client = new CatalogDeliveryQuoteClient(builder, "http://catalog");

  private void expectCatalog(boolean available) {
    server.expect(requestTo("http://catalog/api/v1/products/" + product)).andRespond(withSuccess(
        "{\"id\":\""+product+"\",\"name\":\"Catalog name\",\"status\":\"ACTIVE\",\"globalProduct\":false,\"variants\":[{\"id\":\""+variant+"\",\"productId\":\""+product+"\",\"price\":100,\"active\":true}]}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://catalog/api/v1/stores")).andRespond(withSuccess(
        "[{\"id\":\"store-1\",\"active\":true,\"status\":\"Active\",\"deliveryEnabled\":true,\"pickupEnabled\":true}]", MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://catalog/api/v1/public/store-products?storeId=store-1&productId="+product)).andRespond(withSuccess(
        "[{\"storeId\":\"store-1\",\"productId\":\""+product+"\",\"available\":"+available+",\"priceOverride\":80}]", MediaType.APPLICATION_JSON));
  }

  @Test void usesStorePriceAndCatalogName() {
    expectCatalog(true);
    var item=client.verifyItem("store-1", new CreateOrderItemRequest(product,variant,"Untrusted name",2,new BigDecimal("80.00")),false,false);
    assertThat(item.unitPrice()).isEqualByComparingTo("80");
    assertThat(item.productName()).isEqualTo("Catalog name");
    server.verify();
  }
  @Test void rejectsManipulatedOrStalePrice() {
    expectCatalog(true);
    assertThatThrownBy(()->client.verifyItem("store-1",new CreateOrderItemRequest(product,variant,"Name",1,BigDecimal.ONE),false,false)).hasMessageContaining("price changed");
    server.verify();
  }
  @Test void rejectsUnavailableStoreItem() {
    expectCatalog(false);
    assertThatThrownBy(()->client.verifyItem("store-1",new CreateOrderItemRequest(product,variant,"Name",1,new BigDecimal("80")),false,false)).hasMessageContaining("unavailable at");
    server.verify();
  }
  @Test void rejectsNegativeQuantityWithoutCatalogCall() {
    assertThatThrownBy(()->client.verifyItem("store-1",new CreateOrderItemRequest(product,variant,"Name",-1,new BigDecimal("80")),false,false)).hasMessageContaining("Invalid order item");
    server.verify();
  }
  @Test void catalogFailureDoesNotApproveThePrice() {
    server.expect(requestTo("http://catalog/api/v1/products/"+product)).andRespond(withServerError());
    assertThatThrownBy(()->client.verifyItem("store-1",new CreateOrderItemRequest(product,variant,"Name",1,new BigDecimal("80")),false,false)).hasMessageContaining("Unable to verify");
    server.verify();
  }
}
