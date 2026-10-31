package com.billingsystem.billing_system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:billing-role-access-tests;DB_CLOSE_DELAY=-1",
        "app.auth.owner-password=TestOwnerPassword#2026",
        "app.auth.cashier-password=TestCashierPassword#2026",
        "app.auth.customer-password=TestCustomerPassword#2026"
})
@AutoConfigureMockMvc
class RoleAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void ownerUserHasOwnerRole() {
        UserDetails owner = userDetailsService.loadUserByUsername("owner");
        assertThat(owner.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toList()))
                .contains("ROLE_OWNER");
    }

    @Test
    void cashierUserHasCashierRole() {
        UserDetails cashier = userDetailsService.loadUserByUsername("cashier");
        assertThat(cashier.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toList()))
                .contains("ROLE_CASHIER");
    }

    @Test
    void customerUserHasCustomerRole() {
        UserDetails customer = userDetailsService.loadUserByUsername("customer");
        assertThat(customer.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toList()))
                .contains("ROLE_CUSTOMER");
    }

    @Test
    void builtInPasswordsAreHashedFromConfiguredValues() {
        assertConfiguredPassword("owner", "TestOwnerPassword#2026");
        assertConfiguredPassword("cashier", "TestCashierPassword#2026");
        assertConfiguredPassword("customer", "TestCustomerPassword#2026");
    }

    @Test
    void databaseBackedRoleLoginsRedirectToTheirDashboards() throws Exception {
        assertLoginRedirect("owner", "TestOwnerPassword#2026", "/owner/dashboard");
        assertLoginRedirect("cashier", "TestCashierPassword#2026", "/cashier/dashboard");
        assertLoginRedirect("customer", "TestCustomerPassword#2026", "/customer/home");
    }

    @Test
    void invalidAndDisabledAccountsCannotAuthenticate() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "owner").param("password", "incorrect-password"))
                .andExpect(redirectedUrl("/login?error"));

        jdbc.update("UPDATE app_users SET enabled = FALSE WHERE username = 'customer'");
        try {
            mockMvc.perform(post("/login").with(csrf())
                            .param("username", "customer")
                            .param("password", "TestCustomerPassword#2026"))
                    .andExpect(redirectedUrl("/login?error"));
        } finally {
            jdbc.update("UPDATE app_users SET enabled = TRUE WHERE username = 'customer'");
        }
    }

    @Test
    void publicRegistrationCannotCreatePrivilegedRolesAndRequiresCsrf() throws Exception {
        String username = "cust-" + UUID.randomUUID().toString().substring(0, 8);
        String email = username + "@example.test";
        mockMvc.perform(post("/register")
                        .param("username", username)
                        .param("displayName", "Test Customer")
                        .param("email", email)
                        .param("phone", "123456789")
                        .param("password", "TestRegistrationPassword#2026")
                        .param("role", "OWNER"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", username)
                        .param("displayName", "Test Customer")
                        .param("email", email)
                        .param("phone", "123456789")
                        .param("password", "TestRegistrationPassword#2026")
                        .param("role", "OWNER"))
                .andExpect(redirectedUrl("/login?registered"));

        assertThat(jdbc.queryForObject(
                "SELECT role FROM app_users WHERE username = ?", String.class, username))
                .isEqualTo("CUSTOMER");
        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE username = ?", String.class, username);
        assertThat(passwordEncoder.matches("TestRegistrationPassword#2026", storedHash)).isTrue();
    }

    @Test
    void roleBoundariesAndAnonymousProtectionAreEnforced() throws Exception {
        mockMvc.perform(get("/owner/dashboard"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/owner/dashboard").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/cashier/dashboard").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/customer/home").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountCanChangeOnlyItsOwnPasswordUsingTheCurrentPassword() throws Exception {
        String oldPassword = "TestCustomerPassword#2026";
        String newPassword = "ChangedCustomerPassword#2026";
        try {
            mockMvc.perform(post("/api/account/password")
                            .with(user("customer").roles("CUSTOMER"))
                            .with(csrf())
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword":"%s","newPassword":"%s"}
                                    """.formatted(oldPassword, newPassword)))
                    .andExpect(status().isOk());
            assertThat(passwordEncoder.matches(newPassword, jdbc.queryForObject(
                    "SELECT password_hash FROM app_users WHERE username = 'customer'", String.class))).isTrue();
        } finally {
            jdbc.update("UPDATE app_users SET password_hash = ? WHERE username = 'customer'",
                    passwordEncoder.encode(oldPassword));
        }
    }

    private void assertLoginRedirect(String username, String password, String dashboard) throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(redirectedUrl(dashboard));
    }

    private void assertConfiguredPassword(String username, String configuredPassword) {
        UserDetails user = userDetailsService.loadUserByUsername(username);
        assertThat(user.getPassword()).isNotEqualTo(configuredPassword);
        assertThat(passwordEncoder.matches(configuredPassword, user.getPassword())).isTrue();
    }
}
