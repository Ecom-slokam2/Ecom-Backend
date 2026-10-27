package com.metaarch.catalog.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record LocationProductRequest(
    @NotNull @Valid ProductRequest product,
    @NotEmpty List<@NotNull String> storeIds,
    @NotNull Boolean available) {}
