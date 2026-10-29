package com.billingsystem.billing_system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordResetController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetController.class);

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/forgot-password")
    public String requestReset(@RequestParam(defaultValue = "") String email, Model model) {
        try {
            passwordResetService.requestPasswordReset(email);
            model.addAttribute("message",
                    "If that email matches an active account, password reset instructions have been sent.");
        } catch (PasswordResetService.PasswordResetConfigurationException exception) {
            LOGGER.error("Password reset email is not configured.", exception);
            model.addAttribute("error",
                    "Password reset email is temporarily unavailable. Please contact your store administrator.");
        } catch (MailException exception) {
            LOGGER.error("Could not send a password reset email.", exception);
            model.addAttribute("error",
                    "We could not send the reset email right now. Please try again later.");
        }
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPage(@RequestParam(defaultValue = "") String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("minimumPasswordLength", passwordResetService.minimumPasswordLengthForToken(token));
        model.addAttribute("validToken", passwordResetService.isValidToken(token));
        if (!Boolean.TRUE.equals(model.getAttribute("validToken"))) {
            model.addAttribute("error", "This reset link is invalid or has expired. Request a new link.");
        }
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(
            @RequestParam(defaultValue = "") String token,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "") String confirmPassword,
            Model model) {
        model.addAttribute("token", token);
        int minimumPasswordLength = passwordResetService.minimumPasswordLengthForToken(token);
        model.addAttribute("minimumPasswordLength", minimumPasswordLength);
        if (!passwordResetService.isValidToken(token)) {
            model.addAttribute("validToken", false);
            model.addAttribute("error", "This reset link is invalid or has expired. Request a new link.");
            return "reset-password";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("validToken", true);
            model.addAttribute("error", "The passwords do not match.");
            return "reset-password";
        }
        if (!PasswordPolicy.isValid(password, minimumPasswordLength)) {
            model.addAttribute("validToken", true);
            model.addAttribute("error", "Choose a password between "
                    + minimumPasswordLength + " and 72 UTF-8 bytes.");
            return "reset-password";
        }
        if (!passwordResetService.resetPassword(token, password)) {
            model.addAttribute("validToken", false);
            model.addAttribute("error", "This reset link is invalid or has expired. Request a new link.");
            return "reset-password";
        }
        return "redirect:/login?passwordReset";
    }
}
