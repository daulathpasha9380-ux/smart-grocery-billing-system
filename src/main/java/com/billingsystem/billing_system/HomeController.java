package com.billingsystem.billing_system;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping({"/", "/index", "/dashboard"})
    public String home(Model model) {
        model.addAttribute("activeView", "dashboard");
        return "index";
    }

    @GetMapping("/pos")
    public String pos(Model model) {
        model.addAttribute("activeView", "billing");
        return "index";
    }

    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("activeView", "products");
        return "index";
    }

    @GetMapping("/inventory")
    public String inventory(Model model) {
        model.addAttribute("activeView", "inventory");
        return "index";
    }

    @GetMapping("/customers")
    public String customers(Model model) {
        model.addAttribute("activeView", "customers");
        return "index";
    }

    @GetMapping("/suppliers")
    public String suppliers(Model model) {
        model.addAttribute("activeView", "suppliers");
        return "index";
    }

    @GetMapping("/purchases")
    public String purchases(Model model) {
        model.addAttribute("activeView", "purchases");
        return "index";
    }

    @GetMapping("/sales")
    public String sales(Model model) {
        model.addAttribute("activeView", "sales");
        return "index";
    }

    @GetMapping("/expenses")
    public String expenses(Model model) {
        model.addAttribute("activeView", "expenses");
        return "index";
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("activeView", "reports");
        return "index";
    }

    @GetMapping("/alerts")
    public String alerts(Model model) {
        model.addAttribute("activeView", "alerts");
        return "index";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("activeView", "settings");
        return "index";
    }
}