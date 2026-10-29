package com.billingsystem.billing_system;

import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales/{invoice}/refunds")
@PreAuthorize("hasAnyRole('OWNER', 'CASHIER')")
public class RefundController {
    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    public RefundService.RefundView createRefund(
            @PathVariable String invoice,
            @RequestBody RefundService.RefundRequest request,
            Principal principal,
            Authentication authentication) {
        return refundService.createRefund(invoice, request, principal, isOwner(authentication));
    }

    @GetMapping
    public List<RefundService.RefundView> refunds(
            @PathVariable String invoice,
            Principal principal,
            Authentication authentication) {
        return refundService.refundsForInvoice(invoice, principal, isOwner(authentication));
    }

    private static boolean isOwner(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER"));
    }
}