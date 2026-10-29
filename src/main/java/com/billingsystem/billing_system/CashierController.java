package com.billingsystem.billing_system;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/cashier")
public class CashierController {

    @GetMapping({"", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("pageTitle", "Cashier Dashboard");
        model.addAttribute("roleLabel", "CASHIER");
        return "cashier-dashboard";
    }

    @GetMapping("/pos")
    public String pos(Model model) {
        model.addAttribute("pageTitle", "New Bill");
        model.addAttribute("roleLabel", "CASHIER");
        return "cashier-pos";
    }

    @GetMapping("/held-bills")
    public String heldBills(Model model) {
        model.addAttribute("pageTitle", "Held Bills");
        model.addAttribute("roleLabel", "CASHIER");
        return "cashier-dashboard";
    }

    @GetMapping("/customers")
    public String customers(Model model) {
        model.addAttribute("pageTitle", "Customers");
        model.addAttribute("roleLabel", "CASHIER");
        return "cashier-dashboard";
    }

    @GetMapping("/sales")
    public String sales(Model model) {
        model.addAttribute("pageTitle", "Sales History");
        model.addAttribute("roleLabel", "CASHIER");
        return "cashier-dashboard";
    }

    @GetMapping("/shift")
    public String shift(Model model) {
        model.addAttribute("pageTitle", "Shift Summary");
        model.addAttribute("roleLabel", "CASHIER");
        return "cashier-dashboard";
    }
}
