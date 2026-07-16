package com.waad.tba.modules.preauthorization.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestPreAuthorizationInfoRequest(
        @NotBlank(message = "المعلومات المطلوبة يجب توضيحها")
        @Size(max = 1000, message = "الرسالة لا يمكن أن تتجاوز 1000 حرف")
        String notes) {
}
