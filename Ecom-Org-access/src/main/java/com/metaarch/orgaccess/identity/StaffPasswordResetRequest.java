package com.metaarch.orgaccess.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StaffPasswordResetRequest(
    @NotBlank @Size(min = 8, max = 100) String newPassword) {}
