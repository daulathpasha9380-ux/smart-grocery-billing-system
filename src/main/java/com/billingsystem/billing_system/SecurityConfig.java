package com.billingsystem.billing_system;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/login", "/forgot-password", "/reset-password", "/register", "/api/auth/register", "/css/**", "/js/**", "/images/**", "/product-images/**", "/webjars/**").permitAll()
                .requestMatchers("/", "/index", "/dashboard", "/pos", "/products", "/inventory", "/customers",
                        "/suppliers", "/purchases", "/sales", "/expenses", "/reports", "/alerts", "/settings")
                    .hasRole("OWNER")
                .requestMatchers("/owner/**").hasRole("OWNER")
                .requestMatchers("/api/owner/**").hasRole("OWNER")
                .requestMatchers("/cashier/**").hasAnyRole("OWNER", "CASHIER")
                .requestMatchers("/customer/**").hasRole("CUSTOMER")
                .requestMatchers("/api/smart-cart/**").hasAnyRole("OWNER", "CASHIER", "CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/api/products").hasAnyRole("OWNER", "CASHIER", "CUSTOMER")
                .requestMatchers("/api/customers").hasAnyRole("OWNER", "CASHIER")
                .requestMatchers(HttpMethod.GET, "/api/sales/export").hasRole("OWNER")
                .requestMatchers(HttpMethod.GET, "/api/sales/{invoice}").hasAnyRole("OWNER", "CASHIER")
                .requestMatchers(HttpMethod.GET, "/api/sales").hasRole("OWNER")
                .requestMatchers(HttpMethod.POST, "/api/sales").hasAnyRole("OWNER", "CASHIER")
                .requestMatchers("/api/customer/**").hasRole("CUSTOMER")
                .requestMatchers("/api/orders/**").hasRole("CUSTOMER")
                .requestMatchers("/access-denied").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(roleBasedSuccessHandler())
                .failureUrl("/login?error")
                .permitAll())
            .sessionManagement(session -> session
                .sessionFixation(sessionFixation -> sessionFixation.changeSessionId()))
            .exceptionHandling(exception -> exception.accessDeniedHandler((request, response, denied) -> {
                if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                } else {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    request.getRequestDispatcher("/access-denied").forward(request, response);
                }
            }))
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll());

        return http.build();
    }

    @Bean
    AuthenticationSuccessHandler roleBasedSuccessHandler() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            String role = authentication.getAuthorities().stream()
                    .findFirst()
                    .map(grantedAuthority -> grantedAuthority.getAuthority())
                    .orElse("");

            if ("ROLE_OWNER".equals(role)) {
                response.sendRedirect(request.getContextPath() + "/owner/dashboard");
            } else if ("ROLE_CASHIER".equals(role)) {
                response.sendRedirect(request.getContextPath() + "/cashier/dashboard");
            } else if ("ROLE_CUSTOMER".equals(role)) {
                response.sendRedirect(request.getContextPath() + "/customer/home");
            } else {
                response.sendRedirect(request.getContextPath() + "/");
            }
        };
    }

    @Bean
    UserDetailsService userDetailsService(JdbcTemplate jdbc) {
        return username -> {
            List<RegisteredUser> matches = jdbc.query("""
                    SELECT username, password_hash, role, enabled FROM app_users WHERE username = ?
                    """, (resultSet, rowNum) -> new RegisteredUser(
                    resultSet.getString("username"), resultSet.getString("password_hash"),
                    resultSet.getString("role"), resultSet.getBoolean("enabled")), username);
            if (matches.isEmpty()) {
                throw new UsernameNotFoundException("User account not found.");
            }
            RegisteredUser registered = matches.get(0);
            if (!List.of("OWNER", "CASHIER", "CUSTOMER").contains(registered.role())) {
                throw new UsernameNotFoundException("User account has an invalid role.");
            }
            return User.withUsername(registered.username())
                    .password(registered.passwordHash())
                    .roles(registered.role())
                    .disabled(!registered.enabled())
                    .build();
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private record RegisteredUser(String username, String passwordHash, String role, boolean enabled) {}
}
