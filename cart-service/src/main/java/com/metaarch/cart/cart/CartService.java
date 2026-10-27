package com.metaarch.cart.cart;

import java.math.*;
import java.util.*;
import com.metaarch.cart.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
  private static final BigDecimal ZERO = new BigDecimal("0.00");
  private final CartRepository carts;
  @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
  public CartService(CartRepository carts) { this.carts = carts; }

  @Transactional public CartResponse create(CartRequest request) {
    String storeId = request.storeId().trim();
    jdbc.queryForObject("SELECT id FROM cart_write_lock WHERE id=1 FOR UPDATE", Integer.class);
    var existing = carts.findWithItemsByCustomerIdAndStoreIdAndStatus(request.customerId(), storeId, CartStatus.ACTIVE);
    if (existing.isPresent()) return CartResponse.from(existing.get());
    return CartResponse.from(carts.save(new Cart(request.customerId(), storeId)));
  }
  @Transactional(readOnly = true) public CartResponse get(UUID cartId) { return CartResponse.from(find(cartId)); }
  @Transactional(readOnly = true) public CartResponse getActive(UUID customerId, String storeId) { return CartResponse.from(carts.findWithItemsByCustomerIdAndStoreIdAndStatus(customerId, storeId.trim(), CartStatus.ACTIVE).orElseThrow(() -> new ResourceNotFoundException("Active cart not found"))); }
  @Transactional(readOnly = true) public List<CartResponse> getActiveCarts(UUID customerId) {
    return carts.findAllWithItemsByCustomerIdAndStatus(customerId, CartStatus.ACTIVE).stream().map(CartResponse::from).toList();
  }
  @Transactional public CartResponse addItem(UUID cartId, AddCartItemRequest request) {
    Cart cart = find(cartId); ensureActive(cart);
    CartItem item = cart.getItems().stream().filter(i -> i.getStatus() == CartItemStatus.ACTIVE && i.getProductId().equals(request.productId()) && Objects.equals(i.getVariantId(), request.variantId())).findFirst().orElse(null);
    if (item == null) cart.addItem(new CartItem(request.productId(), request.variantId(), request.productName().trim(), request.quantity(), money(request.unitPrice())));
    else item.setQuantity(Math.addExact(item.getQuantity(), request.quantity()));
    calculate(cart); carts.flush(); return CartResponse.from(cart);
  }
  @Transactional public CartResponse updateQuantity(UUID cartId, UUID itemId, UpdateCartItemQuantityRequest request) {
    Cart cart = find(cartId); ensureActive(cart); CartItem item = findItem(cart, itemId); item.setQuantity(request.quantity()); calculate(cart); return CartResponse.from(cart);
  }
  @Transactional public CartResponse removeItem(UUID cartId, UUID itemId) {
    Cart cart = find(cartId); ensureActive(cart);
    cart.getItems().stream().filter(item -> item.getId().equals(itemId)).findFirst().ifPresent(cart::removeItem);
    calculate(cart); return CartResponse.from(cart);
  }
  @Transactional public CartResponse clear(UUID cartId) {
    Cart cart = find(cartId); ensureActive(cart);
    for (CartItem item : new ArrayList<>(cart.getItems())) {
      if (item.getStatus() == CartItemStatus.ACTIVE) cart.removeItem(item);
    }
    cart.setTotalAmount(ZERO);
    return CartResponse.from(cart);
  }
  @Transactional public CartResponse calculateTotal(UUID cartId) { Cart cart = find(cartId); calculate(cart); return CartResponse.from(cart); }
  @Transactional public CartResponse saveForLater(UUID cartId, UUID itemId) { Cart cart = find(cartId); ensureActive(cart); CartItem item = findItem(cart, itemId); item.setStatus(CartItemStatus.SAVED_FOR_LATER); calculate(cart); return CartResponse.from(cart); }
  @Transactional public CartResponse moveToCart(UUID cartId, UUID itemId) { Cart cart = find(cartId); ensureActive(cart); CartItem item = findItem(cart, itemId); item.setStatus(CartItemStatus.ACTIVE); calculate(cart); return CartResponse.from(cart); }
  private Cart find(UUID id) {
    return (org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()
      ? carts.findWithItemsById(id) : carts.findLockedById(id))
      .orElseThrow(() -> new ResourceNotFoundException("Cart not found: " + id));
  }
  private CartItem findItem(Cart cart, UUID itemId) { return cart.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst().orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId)); }
  private void ensureActive(Cart cart) { if (cart.getStatus() != CartStatus.ACTIVE) throw new InvalidCartStateException("Only active carts can be modified"); }
  private void calculate(Cart cart) { cart.getItems().forEach(CartItem::recalculateTotal); cart.setTotalAmount(cart.getItems().stream().filter(item -> item.getStatus() == CartItemStatus.ACTIVE).map(CartItem::getTotalPrice).reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP)); }
  private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}
