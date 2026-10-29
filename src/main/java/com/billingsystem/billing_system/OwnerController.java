package com.billingsystem.billing_system;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/owner")
public class OwnerController {

    @GetMapping({"", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("pageTitle", "Owner Dashboard");
        model.addAttribute("roleLabel", "OWNER");
        return "owner-dashboard";
    }

    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("pageTitle", "Products");
        model.addAttribute("roleLabel", "OWNER");
        return "owner-dashboard";
    }

    @GetMapping("/inventory")
    public String inventory(Model model) {
        model.addAttribute("pageTitle", "Inventory");
        model.addAttribute("roleLabel", "OWNER");
        return "owner-dashboard";
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("pageTitle", "Reports & Analytics");
        model.addAttribute("roleLabel", "OWNER");
        return "owner-dashboard";
    }

    @GetMapping("/suppliers")
    public String suppliers(Model model) {
        model.addAttribute("pageTitle", "Suppliers");
        model.addAttribute("roleLabel", "OWNER");
        return "owner-dashboard";
    }

    @GetMapping("/purchases")
    public String purchases(Model model) {
        model.addAttribute("pageTitle", "Purchases");
        model.addAttribute("roleLabel", "OWNER");
        return "owner-dashboard";
    }

    @GetMapping("/expenses")
    public String expenses(Model model) {
        model.addAttribute("pageTitle", "Expenses");
        model.addAttribute("roleLabel", "OWNER");
        return "owner-dashboard";
    }

    @GetMapping("/employees")
    public String employees(Model model) {
        model.addAttribute("pageTitle", "Employees");
        model.addAttribute("roleLabel", "OWNER");
        return "owner-dashboard";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("pageTitle", "Store Settings");
        model.addAttribute("roleLabel", "OWNER");
        return "owner-dashboard";
    }
}
