package com.billingsystem.billing_system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:billing-role-access-tests;DB_CLOSE_DELAY=-1",
        "spring.mail.host=mail.example.test",
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

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void resetMailMock() {
        reset(mailSender);
    }

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
    void developmentInitializerDoesNotOverwriteExistingSeededPasswords() {
        String ownerHash = passwordHash("owner");
        String cashierHash = passwordHash("cashier");
        String customerHash = passwordHash("customer");

        new ApplicationDataInitializer(jdbc, passwordEncoder, "", "", "", "", "").run();

        assertThat(passwordHash("owner")).isEqualTo(ownerHash);
        assertThat(passwordHash("cashier")).isEqualTo(cashierHash);
        assertThat(passwordHash("customer")).isEqualTo(customerHash);
    }

    @Test
    void configuredRecoveryEmailsFillOnlyMissingSeededAccountEmails() {
        String ownerEmail = jdbc.queryForObject(
                "SELECT email FROM app_users WHERE username = 'owner'", String.class);
        String cashierEmail = jdbc.queryForObject(
                "SELECT email FROM app_users WHERE username = 'cashier'", String.class);
        try {
            jdbc.update("UPDATE app_users SET email = NULL WHERE username IN ('owner', 'cashier')");
            new ApplicationDataInitializer(jdbc, passwordEncoder, "", "", "",
                    "owner-reset@example.test", "cashier-reset@example.test").run();
            assertThat(jdbc.queryForObject(
                    "SELECT email FROM app_users WHERE username = 'owner'", String.class))
                    .isEqualTo("owner-reset@example.test");
            assertThat(jdbc.queryForObject(
                    "SELECT email FROM app_users WHERE username = 'cashier'", String.class))
                    .isEqualTo("cashier-reset@example.test");

            new ApplicationDataInitializer(jdbc, passwordEncoder, "", "", "",
                    "replacement@example.test", "replacement@example.test").run();
            assertThat(jdbc.queryForObject(
                    "SELECT email FROM app_users WHERE username = 'owner'", String.class))
                    .isEqualTo("owner-reset@example.test");
            assertThat(jdbc.queryForObject(
                    "SELECT email FROM app_users WHERE username = 'cashier'", String.class))
                    .isEqualTo("cashier-reset@example.test");
        } finally {
            jdbc.update("UPDATE app_users SET email = ? WHERE username = 'owner'", ownerEmail);
            jdbc.update("UPDATE app_users SET email = ? WHERE username = 'cashier'", cashierEmail);
        }
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
                        .param("password", "Shopper8")
                        .param("role", "OWNER"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", username)
                        .param("displayName", "Test Customer")
                        .param("email", email)
                        .param("phone", "123456789")
                        .param("password", "Shopper8")
                        .param("role", "OWNER"))
                .andExpect(redirectedUrl("/login?registered"));

        assertThat(jdbc.queryForObject(
                "SELECT role FROM app_users WHERE username = ?", String.class, username))
                .isEqualTo("CUSTOMER");
        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE username = ?", String.class, username);
        assertThat(passwordEncoder.matches("Shopper8", storedHash)).isTrue();
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
    void forgotPasswordHelpIsPublicAndLinkedFromLogin() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/forgot-password")));

        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "password reset link")));
    }

    @Test
    void passwordResetEmailChangesPasswordAndInvalidatesTheLink() throws Exception {
        String email = "customer@freshledger.local";
        String originalHash = passwordHash("customer");
        String newPassword = "Reset8pw";
        try {
            mockMvc.perform(post("/forgot-password").with(csrf()).param("email", email))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "If that email matches an active account")));

            ArgumentCaptor<SimpleMailMessage> messageCaptor =
                    ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(messageCaptor.capture());
            SimpleMailMessage sentMessage = messageCaptor.getValue();
            assertThat(sentMessage.getTo()).containsExactly(email);
            Matcher tokenMatcher = Pattern.compile("token=([A-Za-z0-9_-]+)")
                    .matcher(sentMessage.getText());
            assertThat(tokenMatcher.find()).isTrue();
            String token = tokenMatcher.group(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM password_reset_tokens WHERE token_hash = ?",
                    Integer.class, token)).isZero();

            mockMvc.perform(get("/reset-password").param("token", token))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Set a new password")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("between 8 and 72 UTF-8 bytes")));

            mockMvc.perform(post("/reset-password").with(csrf())
                            .param("token", token)
                            .param("password", newPassword)
                            .param("confirmPassword", newPassword))
                    .andExpect(redirectedUrl("/login?passwordReset"));
            assertThat(passwordEncoder.matches(newPassword, passwordHash("customer"))).isTrue();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM password_reset_tokens WHERE username = 'customer'",
                    Integer.class)).isZero();

            mockMvc.perform(post("/reset-password").with(csrf())
                            .param("token", token)
                            .param("password", newPassword)
                            .param("confirmPassword", newPassword))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("invalid or has expired")));
        } finally {
            jdbc.update("UPDATE app_users SET password_hash = ? WHERE username = 'customer'", originalHash);
            jdbc.update("DELETE FROM password_reset_tokens WHERE username = 'customer'");
        }
    }

    @Test
    void accountCanChangeOnlyItsOwnPasswordUsingTheCurrentPassword() throws Exception {
        String oldPassword = "TestCustomerPassword#2026";
        String newPassword = "Change8p";
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

            mockMvc.perform(post("/api/account/password")
                            .with(user("owner").roles("OWNER"))
                            .with(csrf())
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword":"TestOwnerPassword#2026","newPassword":"Change8p"}
                                    """))
                    .andExpect(status().isBadRequest());
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

    private String passwordHash(String username) {
        return jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE username = ?", String.class, username);
    }
}
