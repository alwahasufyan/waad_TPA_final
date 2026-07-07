package com.waad.tba.core.email;

import org.springframework.stereotype.Service;

@Service

public class EmailTemplateService {

    public String otpTemplate(String fullName, String otp) {
        return """
                <div style="font-family: Arial, sans-serif; padding: 20px;">
                  <h2 style="color:#0d6efd;">TBA-WAAD System</h2>
                  <p>Dear <strong>%s</strong>,</p>
                  <p>You have requested a password reset.</p>
                  <p>Your OTP code:</p>
                  <h1 style="letter-spacing: 5px; color:#0d6efd;">%s</h1>
                  <p>This code will expire in <strong>10 minutes</strong>.</p>
                  <br>
                  <p>If you did not request this, please ignore this email.</p>
                  <hr>
                  <small>TBA-WAAD Health Insurance Platform</small>
                </div>
                """.formatted(fullName, otp);
    }

    public String welcomeTemplate(String fullName) {
        return """
                <div style="font-family: Arial; padding: 18px;">
                  <h2 style="color:#28a745;">Welcome to TBA-WAAD!</h2>
                  <p>Hello <strong>%s</strong>,</p>
                  <p>Your account has been successfully created.</p>
                  <p>You can now log in and access your dashboard.</p>
                  <br>
                  <small>TBA-WAAD Health Insurance Platform</small>
                </div>
                """.formatted(fullName);
    }

    public String resetPasswordSuccess(String fullName) {
        return """
                <div style="font-family: Arial; padding: 18px;">
                  <h2>Password Updated Successfully</h2>
                  <p>Dear <strong>%s</strong>,</p>
                  <p>Your password has been changed successfully.</p>
                  <p>If this was not you, contact support immediately.</p>
                  <hr>
                  <small>TBA-WAAD Support</small>
                </div>
                """.formatted(fullName);
    }
}
