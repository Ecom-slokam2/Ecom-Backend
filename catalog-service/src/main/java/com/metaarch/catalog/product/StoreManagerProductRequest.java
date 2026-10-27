package com.metaarch.catalog.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record StoreManagerProductRequest(@NotNull @Valid ProductRequest product, @NotNull Boolean available) {}
