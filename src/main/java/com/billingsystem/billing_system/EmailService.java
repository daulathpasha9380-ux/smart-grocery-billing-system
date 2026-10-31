package com.billingsystem.billing_system;

import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final AtomicReference<SentEmail> lastSentEmail = new AtomicReference<>();

    public EmailService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${spring.mail.username:no-reply@freshledger.com}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = (fromAddress == null || fromAddress.isBlank()) ? "no-reply@freshledger.com" : fromAddress;
    }

    public boolean sendPasswordResetEmail(String toEmail, String recipientName, String resetLink) {
        String displayName = (recipientName != null && !recipientName.isBlank()) ? recipientName : "Valued Customer";
        SentEmail record = new SentEmail(toEmail, displayName, resetLink, Instant.now());
        lastSentEmail.set(record);

        LOGGER.info("================================================================================");
        LOGGER.info("FRESHLEDGER PASSWORD RESET VERIFICATION LINK");
        LOGGER.info("To: {} ({})", toEmail, displayName);
        LOGGER.info("Verification URL: {}", resetLink);
        LOGGER.info("Expires in: 30 minutes");
        LOGGER.info("================================================================================");

        if (mailSender == null) {
            LOGGER.info("No active JavaMailSender configured. Verification link recorded for local/dev use.");
            return true;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, "FreshLedger Security");
            helper.setTo(toEmail);
            helper.setSubject("Reset Your FreshLedger Password");

            String html = buildEmailHtml(displayName, resetLink);
            helper.setText(html, true);

            mailSender.send(message);
            LOGGER.info("Password reset email successfully dispatched over SMTP to {}", toEmail);
            return true;
        } catch (Exception ex) {
            LOGGER.warn("Could not dispatch email over SMTP ({}). Dev link remains valid: {}", ex.getMessage(), resetLink);
            return false;
        }
    }

    public SentEmail getLastSentEmail() {
        return lastSentEmail.get();
    }

    public void clearLastSentEmail() {
        lastSentEmail.set(null);
    }

    private String buildEmailHtml(String recipientName, String resetLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 24px; color: #1e293b; }
                    .card { max-width: 560px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.06); }
                    .header { background: #15803d; color: #ffffff; padding: 28px 24px; text-align: center; }
                    .header h1 { margin: 0; font-size: 26px; font-weight: 700; letter-spacing: -0.5px; }
                    .body { padding: 32px 28px; }
                    .greeting { font-size: 18px; font-weight: 600; margin-bottom: 14px; color: #0f172a; }
                    .text { font-size: 15px; line-height: 1.6; color: #475569; margin-bottom: 20px; }
                    .btn-wrap { text-align: center; margin: 32px 0; }
                    .btn { background-color: #15803d; color: #ffffff !important; padding: 14px 32px; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 16px; display: inline-block; box-shadow: 0 2px 4px rgba(21,128,61,0.2); }
                    .expiry-note { background-color: #fefce8; border: 1px solid #fef08a; border-radius: 8px; padding: 12px 16px; font-size: 14px; color: #854d0e; margin: 24px 0; }
                    .link-alt { font-size: 12px; color: #64748b; word-break: break-all; margin-top: 24px; padding-top: 16px; border-top: 1px solid #f1f5f9; }
                    .link-alt a { color: #15803d; }
                    .footer { background: #f8fafc; padding: 18px 24px; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #f1f5f9; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="header">
                        <h1>FreshLedger</h1>
                    </div>
                    <div class="body">
                        <div class="greeting">Hello %s,</div>
                        <p class="text">We received a request to reset the password for your FreshLedger account. Click the button below to choose your new password:</p>
                        <div class="btn-wrap">
                            <a href="%s" class="btn">Reset Password &rarr;</a>
                        </div>
                        <div class="expiry-note">
                            &#9888; <strong>Security Notice:</strong> This verification link will expire in <strong>30 minutes</strong> and can only be used once.
                        </div>
                        <p class="text" style="font-size:13px; color:#64748b;">If you did not make this request, you can safely ignore this email. Your existing password will remain unchanged.</p>
                        <div class="link-alt">
                            Having trouble with the button? Copy and paste this link into your browser:<br/>
                            <a href="%s">%s</a>
                        </div>
                    </div>
                    <div class="footer">
                        &copy; FreshLedger Grocery Billing System &bull; Secure Password Reset Service
                    </div>
                </div>
            </body>
            </html>
            """.formatted(recipientName, resetLink, resetLink, resetLink);
    }

    public record SentEmail(String toEmail, String recipientName, String resetLink, Instant sentAt) {}
}
