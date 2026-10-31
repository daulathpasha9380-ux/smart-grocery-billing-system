package com.billingsystem.billing_system;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
public class ForgotPasswordController {

    private final PasswordResetService passwordResetService;

    public ForgotPasswordController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(
            @RequestParam("email") String email,
            HttpServletRequest request,
            Model model) {

        String baseUrl = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(request.getContextPath())
                .build()
                .toUriString();

        PasswordResetService.ResetRequestResult result = passwordResetService.initiatePasswordReset(email, baseUrl);

        if (result.success()) {
            model.addAttribute("requestSent", true);
            model.addAttribute("targetEmail", result.email());
            model.addAttribute("verificationLink", result.verificationLink());
            model.addAttribute("successMessage", result.message());
        } else {
            model.addAttribute("errorMessage", result.message());
            model.addAttribute("submittedEmail", email);
        }

        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(
            @RequestParam(value = "token", required = false) String token,
            Model model) {

        if (token == null || token.isBlank()) {
            model.addAttribute("invalidToken", true);
            model.addAttribute("tokenError", "No verification token provided. Please request a new password reset link.");
            return "reset-password";
        }

        Optional<PasswordResetService.ResetTokenDetails> tokenOpt = passwordResetService.validateToken(token);
        if (tokenOpt.isEmpty()) {
            model.addAttribute("invalidToken", true);
            model.addAttribute("tokenError", "This password reset verification link is invalid, has expired, or has already been used.");
            return "reset-password";
        }

        PasswordResetService.ResetTokenDetails details = tokenOpt.get();
        model.addAttribute("token", details.token());
        model.addAttribute("username", details.username());
        model.addAttribute("displayName", details.displayName());
        model.addAttribute("targetEmail", details.email());
        model.addAttribute("invalidToken", false);

        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(
            @RequestParam("token") String token,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model,
            RedirectAttributes redirectAttributes) {

        PasswordResetService.ResetCompletionResult result = passwordResetService.completePasswordReset(token, newPassword, confirmPassword);

        if (result.success()) {
            redirectAttributes.addFlashAttribute("resetSuccessMessage", "Your password has been reset successfully! Please sign in with your new password.");
            return "redirect:/login?resetSuccess";
        }

        // Error during reset
        Optional<PasswordResetService.ResetTokenDetails> tokenOpt = passwordResetService.validateToken(token);
        if (tokenOpt.isEmpty()) {
            model.addAttribute("invalidToken", true);
            model.addAttribute("tokenError", result.message());
        } else {
            PasswordResetService.ResetTokenDetails details = tokenOpt.get();
            model.addAttribute("token", token);
            model.addAttribute("username", details.username());
            model.addAttribute("displayName", details.displayName());
            model.addAttribute("targetEmail", details.email());
            model.addAttribute("invalidToken", false);
            model.addAttribute("errorMessage", result.message());
        }

        return "reset-password";
    }
}
