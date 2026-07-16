package com.waad.tba.modules.preauthorization.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PartialApprovePreAuthorizationRequest(
        @NotNull(message = "المبلغ المعتمد مطلوب")
        @DecimalMin(value = "0.01", message = "المبلغ المعتمد يجب أن يكون أكبر من صفر")
        @Digits(integer = 13, fraction = 2, message = "المبلغ المعتمد غير صالح")
        BigDecimal approvedAmount,
        @NotBlank(message = "سبب الموافقة الجزئية مطلوب")
        @Size(max = 1000, message = "السبب لا يمكن أن يتجاوز 1000 حرف")
        String reason) {
}
