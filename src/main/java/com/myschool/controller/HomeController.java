package com.myschool.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication) {
        boolean teacher = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_TEACHER"::equals);
        return teacher ? "redirect:/teacher/dashboard" : "redirect:/student/dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
