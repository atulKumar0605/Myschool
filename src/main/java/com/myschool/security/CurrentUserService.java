package com.myschool.security;

import com.myschool.service.SchoolService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final SchoolService schoolService;

    public CurrentUserService(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    public SchoolService.SessionUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found.");
        }
        return schoolService.findSessionUser(authentication.getName())
            .orElseThrow(() -> new IllegalStateException("Authenticated user is missing from the database."));
    }
}
