package com.metaarch.cart.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
  @Mock private CartRepository carts;

  @Test
  void saveForLaterMovesItemOutOfActiveTotal() {
    UUID cartId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    Cart cart = new Cart(UUID.randomUUID(), "SR Nagar Store");
    ReflectionTestUtils.setField(cart, "id", cartId);
    CartItem item = new CartItem(UUID.randomUUID(), null, "Gongura Red Chilli Pickle", 2, new BigDecimal("249.00"));
    ReflectionTestUtils.setField(item, "id", itemId);
    cart.addItem(item);

    when(carts.findLockedById(cartId)).thenReturn(Optional.of(cart));

    CartResponse response = new CartService(carts).saveForLater(cartId, itemId);

    assertThat(response.totalAmount()).isEqualByComparingTo("0.00");
    assertThat(response.items()).isEmpty();
    assertThat(response.savedForLaterItems()).singleElement().satisfies(saved -> {
      assertThat(saved.id()).isEqualTo(itemId);
      assertThat(saved.status()).isEqualTo(CartItemStatus.SAVED_FOR_LATER);
      assertThat(saved.totalPrice()).isEqualByComparingTo("498.00");
    });
  }

  @Test
  void moveToCartRestoresSavedItemToActiveTotal() {
    UUID cartId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    Cart cart = new Cart(UUID.randomUUID(), "SR Nagar Store");
    ReflectionTestUtils.setField(cart, "id", cartId);
    CartItem item = new CartItem(UUID.randomUUID(), null, "Gongura Red Chilli Pickle", 1, new BigDecimal("249.00"));
    ReflectionTestUtils.setField(item, "id", itemId);
    item.setStatus(CartItemStatus.SAVED_FOR_LATER);
    cart.addItem(item);

    when(carts.findLockedById(cartId)).thenReturn(Optional.of(cart));

    CartResponse response = new CartService(carts).moveToCart(cartId, itemId);

    assertThat(response.totalAmount()).isEqualByComparingTo("249.00");
    assertThat(response.savedForLaterItems()).isEmpty();
    assertThat(response.items()).singleElement().satisfies(active -> {
      assertThat(active.id()).isEqualTo(itemId);
      assertThat(active.status()).isEqualTo(CartItemStatus.ACTIVE);
    });
  }

  @Test
  void clearRemovesActiveItemsAndKeepsSavedForLaterItems() {
    UUID cartId = UUID.randomUUID();
    Cart cart = new Cart(UUID.randomUUID(), "SR Nagar Store");
    ReflectionTestUtils.setField(cart, "id", cartId);
    cart.addItem(new CartItem(UUID.randomUUID(), null, "Active item", 1, new BigDecimal("10.00")));
    CartItem saved = new CartItem(UUID.randomUUID(), null, "Saved item", 2, new BigDecimal("5.00"));
    saved.setStatus(CartItemStatus.SAVED_FOR_LATER);
    cart.addItem(saved);
    when(carts.findLockedById(cartId)).thenReturn(Optional.of(cart));

    CartResponse response = new CartService(carts).clear(cartId);

    assertThat(response.totalAmount()).isEqualByComparingTo("0.00");
    assertThat(response.items()).isEmpty();
    assertThat(response.savedForLaterItems()).singleElement().satisfies(item -> {
      assertThat(item.productName()).isEqualTo("Saved item");
      assertThat(item.quantity()).isEqualTo(2);
    });
  }
}
