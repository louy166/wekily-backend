package com.example.backendwekily.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ✅ Sert le panel admin HTML
 * /admin       → /admin/index.html
 * /admin/      → /admin/index.html
 */
@Controller
public class AdminViewController {

    @GetMapping({"/admin", "/admin/"})
    public String adminIndex() {
        return "forward:/admin/index.html";
    }
}