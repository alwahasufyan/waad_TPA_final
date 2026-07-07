package com.waad.tba.modules.systemadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for Change Password Request
 * Used by authenticated users to change their own password
 * 
 * Validation Rules:
 * - currentPassword: Required
 * - newPassword: Required, minimum 8 characters
 * - confirmPassword: Required, must match newPassword
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "كلمة المرور الحالية مطلوبة")
    private String currentPassword;

    @NotBlank(message = "كلمة المرور الجديدة مطلوبة")
    @Size(min = 8, message = "كلمة المرور يجب أن تكون 8 أحرف على الأقل")
    private String newPassword;

    @NotBlank(message = "تأكيد كلمة المرور مطلوب")
    private String confirmPassword;
}
