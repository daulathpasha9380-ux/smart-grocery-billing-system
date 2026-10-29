package com.billingsystem.billing_system;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/customer")
public class CustomerController {

    @GetMapping({"", "/home"})
    public String home(Model model) {
        model.addAttribute("pageTitle", "Customer Home");
        model.addAttribute("roleLabel", "CUSTOMER");
        return "customer-home";
    }

    @GetMapping("/shop")
    public String shop(Model model) {
        model.addAttribute("pageTitle", "Shop");
        model.addAttribute("roleLabel", "CUSTOMER");
        return "customer-home";
    }

    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("pageTitle", "Browse Products");
        model.addAttribute("roleLabel", "CUSTOMER");
        return "customer-home";
    }

    @GetMapping("/cart")
    public String cart(Model model) {
        model.addAttribute("pageTitle", "My Cart");
        model.addAttribute("roleLabel", "CUSTOMER");
        return "customer-home";
    }

    @GetMapping("/orders")
    public String orders(Model model) {
        model.addAttribute("pageTitle", "My Orders");
        model.addAttribute("roleLabel", "CUSTOMER");
        return "customer-home";
    }

    @GetMapping("/bills")
    public String bills(Model model) {
        model.addAttribute("pageTitle", "My Bills");
        model.addAttribute("roleLabel", "CUSTOMER");
        return "customer-home";
    }

    @GetMapping("/loyalty")
    public String loyalty(Model model) {
        model.addAttribute("pageTitle", "Loyalty Points");
        model.addAttribute("roleLabel", "CUSTOMER");
        return "customer-home";
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        model.addAttribute("pageTitle", "Profile");
        model.addAttribute("roleLabel", "CUSTOMER");
        return "customer-home";
    }
}
