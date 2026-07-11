package com.myschool.security;

import com.myschool.service.SchoolService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SchoolUserDetailsService implements UserDetailsService {
    private final SchoolService schoolService;

    public SchoolUserDetailsService(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SchoolService.AuthUser user = schoolService.findAuthUser(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new User(
            user.username(),
            user.password(),
            user.enabled(),
            true,
            true,
            true,
            List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))
        );
    }
}
