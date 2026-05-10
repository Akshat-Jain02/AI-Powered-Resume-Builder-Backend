package com.resumeai.notification.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@resumeai.com}")
    private String fromAddress;

    private static final String BRAND_COLOR   = "#6C63FF";
    private static final String BG_COLOR      = "#f4f4f8";
    private static final String CARD_BG       = "#ffffff";
    private static final String TEXT_DARK     = "#2d2d2d";
    private static final String TEXT_MUTED    = "#6b7280";
    private static final String GREEN         = "#22c55e";
    private static final String RED           = "#ef4444";
    private static final String ORANGE        = "#f97316";

    // ──────────────────────────────────────────────────────────────
    // PAYMENT SUCCESS
    // ──────────────────────────────────────────────────────────────

    @Async
    public void sendPaymentSuccessEmail(String to, String username, String planName,
                                        int creditsGranted, int totalCredits,
                                        double amountPaid, String orderId,
                                        String razorpayPaymentId) {
        String subject = "✅ Payment Successful — " + creditsGranted + " Credits Added!";
        String html = baseTemplate(subject,
            "<h2 style='color:" + GREEN + ";margin:0 0 8px'>Payment Successful! 🎉</h2>" +
            "<p style='color:" + TEXT_MUTED + ";margin:0 0 24px'>Your payment was processed and credits are ready to use.</p>" +

            infoCard(
                row("Plan",             planName) +
                row("Credits Added",    "<strong style='color:" + GREEN + "'>" + creditsGranted + " credits</strong>") +
                row("Total Credits",    String.valueOf(totalCredits)) +
                row("Amount Paid",      "₹" + String.format("%.2f", amountPaid)) +
                row("Order ID",         orderId) +
                row("Payment ID",       razorpayPaymentId)
            ) +

            "<p style='margin:24px 0 8px'>You can now use your credits to:</p>" +
            "<ul style='margin:0;padding-left:20px;color:" + TEXT_DARK + "'>" +
            "<li>🤖 AI Resume Analysis & Feedback</li>" +
            "<li>📊 ATS Score Checker</li>" +
            "<li>🎯 Job Match Recommendations</li>" +
            "</ul>" +

            ctaButton("Go to Dashboard", "http://localhost:5173") +
            footer()
        );
        sendHtml(to, subject, html);
    }

    // ──────────────────────────────────────────────────────────────
    // PAYMENT FAILED
    // ──────────────────────────────────────────────────────────────

    @Async
    public void sendPaymentFailedEmail(String to, String username, String planName,
                                       String orderId, String reason) {
        String subject = "❌ Payment Failed — " + planName;
        String html = baseTemplate(subject,
            "<h2 style='color:" + RED + ";margin:0 0 8px'>Payment Failed</h2>" +
            "<p style='color:" + TEXT_MUTED + ";margin:0 0 24px'>Unfortunately, your payment could not be processed. No amount has been deducted.</p>" +

            infoCard(
                row("Plan",     planName) +
                row("Order ID", orderId) +
                row("Reason",   reason != null ? reason : "Unknown error")
            ) +

            "<p style='margin:24px 0'>Please try again. If the problem persists, contact support.</p>" +
            ctaButton("Retry Payment", "http://localhost:5173/payment") +
            footer()
        );
        sendHtml(to, subject, html);
    }

    // ──────────────────────────────────────────────────────────────
    // ATS SCORE RESULT
    // ──────────────────────────────────────────────────────────────

    @Async
    public void sendAtsScoreEmail(String to, String username, String fileName,
                                  String atsScore, List<String> feedback,
                                  List<String> suggestions) {
        String subject = "📊 Your ATS Score: " + atsScore + "/100 — " + fileName;

        int score = parseScore(atsScore);
        String scoreColor = score >= 70 ? GREEN : (score >= 50 ? ORANGE : RED);
        String scoreLabel = score >= 70 ? "Great!" : (score >= 50 ? "Average" : "Needs Work");

        String feedbackHtml = listItems(feedback, "📋");
        String suggestionsHtml = listItems(suggestions, "💡");

        String html = baseTemplate(subject,
            "<h2 style='margin:0 0 8px'>ATS Score Report</h2>" +
            "<p style='color:" + TEXT_MUTED + ";margin:0 0 24px'>File: <strong>" + fileName + "</strong></p>" +

            // Score circle
            "<div style='text-align:center;margin:0 0 24px'>" +
            "<div style='display:inline-block;background:" + scoreColor + ";color:#fff;" +
                 "border-radius:50%;width:100px;height:100px;line-height:100px;" +
                 "font-size:2rem;font-weight:700;text-align:center'>" +
            atsScore + "</div>" +
            "<p style='margin:8px 0 0;font-weight:600;color:" + scoreColor + "'>" + scoreLabel + "</p>" +
            "</div>" +

            section("📋 Feedback", feedbackHtml) +
            section("💡 Suggestions to Improve", suggestionsHtml) +

            ctaButton("Improve Your Resume", "http://localhost:5173/ai-analyzer") +
            footer()
        );
        sendHtml(to, subject, html);
    }

    // ──────────────────────────────────────────────────────────────
    // RESUME ANALYSIS RESULT
    // ──────────────────────────────────────────────────────────────

    @Async
    public void sendResumeAnalysisEmail(String to, String username, String fileName,
                                        String summary, List<String> strengths,
                                        List<String> improvements, List<String> keywords,
                                        String overallScore, String targetRole) {
        String subject = "🤖 AI Resume Feedback Ready — Score: " + overallScore;
        String html = baseTemplate(subject,
            "<h2 style='margin:0 0 8px'>AI Resume Analysis Complete</h2>" +
            "<p style='color:" + TEXT_MUTED + ";margin:0 0 24px'>File: <strong>" + fileName + "</strong></p>" +

            infoCard(
                row("Overall Score", "<strong style='color:" + BRAND_COLOR + "'>" + overallScore + "</strong>") +
                row("Target Role",   targetRole != null ? targetRole : "—")
            ) +

            section("📝 Summary", "<p style='margin:0;color:" + TEXT_DARK + "'>" + summary + "</p>") +
            section("✅ Strengths",         listItems(strengths, "✅")) +
            section("⚠️ Areas to Improve",  listItems(improvements, "⚠️")) +
            section("🔑 Missing Keywords",  listItems(keywords, "🔑")) +

            ctaButton("View Full Analysis", "http://localhost:5173/ai-analyzer") +
            footer()
        );
        sendHtml(to, subject, html);
    }

    // ──────────────────────────────────────────────────────────────
    // CREDIT DEDUCTED
    // ──────────────────────────────────────────────────────────────

    @Async
    public void sendCreditDeductedEmail(String to, String username, String feature,
                                        int creditsUsed, int remainingCredits) {
        String subject = "💳 " + creditsUsed + " Credit(s) Used — " + feature;
        String html = baseTemplate(subject,
            "<h2 style='margin:0 0 8px'>Credits Used</h2>" +
            "<p style='color:" + TEXT_MUTED + ";margin:0 0 24px'>" + feature + " completed successfully.</p>" +

            infoCard(
                row("Feature",            feature) +
                row("Credits Used",       String.valueOf(creditsUsed)) +
                row("Remaining Credits",  "<strong style='color:" + (remainingCredits <= 2 ? RED : GREEN) + "'>" +
                                          remainingCredits + " credits</strong>")
            ) +

            (remainingCredits <= 2
                ? "<div style='background:#fef2f2;border:1px solid #fecaca;border-radius:8px;padding:12px;margin:16px 0'>" +
                  "⚠️ <strong>Low credits!</strong> You have only " + remainingCredits + " credit(s) left. Top up to continue using AI features." +
                  "</div>" + ctaButton("Buy More Credits", "http://localhost:5173/payment")
                : "") +

            footer()
        );
        sendHtml(to, subject, html);
    }

    // ──────────────────────────────────────────────────────────────
    // LOW CREDITS ALERT
    // ──────────────────────────────────────────────────────────────

    @Async
    public void sendLowCreditsAlert(String to, String username, int remainingCredits) {
        String subject = "⚠️ Low Credits Alert — Only " + remainingCredits + " Left";
        String html = baseTemplate(subject,
            "<h2 style='color:" + ORANGE + ";margin:0 0 8px'>You're Running Low on Credits!</h2>" +
            "<p style='color:" + TEXT_MUTED + ";margin:0 0 24px'>You have only <strong>" + remainingCredits +
            " credit(s)</strong> remaining. Top up now to keep using AI features without interruption.</p>" +

            infoCard(row("Remaining Credits", "<strong style='color:" + RED + "'>" + remainingCredits + "</strong>")) +

            ctaButton("Buy Credits Now", "http://localhost:5173/payment") +
            footer()
        );
        sendHtml(to, subject, html);
    }

    // ──────────────────────────────────────────────────────────────
    // WELCOME ON REGISTRATION
    // ──────────────────────────────────────────────────────────────

    @Async
    public void sendWelcomeEmail(String to, String username) {
        String subject = "🎉 Welcome to ResumeAI, " + username + "!";
        String html = baseTemplate(subject,
            "<h2 style='margin:0 0 8px'>Welcome to ResumeAI! 🚀</h2>" +
            "<p style='color:" + TEXT_MUTED + ";margin:0 0 24px'>Your account has been created successfully. " +
            "Here's everything you can do with ResumeAI:</p>" +

            "<ul style='margin:0 0 24px;padding-left:20px;color:" + TEXT_DARK + ";line-height:2'>" +
            "<li>🤖 <strong>AI Resume Analysis</strong> — Get detailed feedback powered by Gemini AI</li>" +
            "<li>📊 <strong>ATS Score Checker</strong> — Know your resume's ATS compatibility</li>" +
            "<li>🎨 <strong>Resume Builder</strong> — Create stunning resumes with professional templates</li>" +
            "<li>🎯 <strong>Job Matcher</strong> — Find jobs that match your skills</li>" +
            "</ul>" +

            "<p style='margin:0 0 24px'>Purchase credits to unlock AI-powered features and supercharge your job search!</p>" +
            ctaButton("Get Started", "http://localhost:5173") +
            footer()
        );
        sendHtml(to, subject, html);
    }

    // ──────────────────────────────────────────────────────────────
    // HTML helpers
    // ──────────────────────────────────────────────────────────────

    private String baseTemplate(String title, String content) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
               "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
               "<title>" + title + "</title></head>" +
               "<body style='margin:0;padding:0;font-family:Arial,Helvetica,sans-serif;background:" + BG_COLOR + ";color:" + TEXT_DARK + "'>" +
               "<table width='100%' cellpadding='0' cellspacing='0' style='background:" + BG_COLOR + ";padding:32px 0'>" +
               "<tr><td align='center'>" +
               "<table width='600' cellpadding='0' cellspacing='0' style='max-width:600px;width:100%'>" +

               // Header
               "<tr><td style='background:" + BRAND_COLOR + ";border-radius:12px 12px 0 0;padding:24px 32px;text-align:center'>" +
               "<h1 style='margin:0;color:#fff;font-size:1.6rem;letter-spacing:-0.5px'>ResumeAI</h1>" +
               "<p style='margin:4px 0 0;color:rgba(255,255,255,0.75);font-size:0.85rem'>Your AI-Powered Career Partner</p>" +
               "</td></tr>" +

               // Body
               "<tr><td style='background:" + CARD_BG + ";padding:32px;border-radius:0 0 12px 12px'>" +
               content +
               "</td></tr>" +

               "</table></td></tr></table>" +
               "</body></html>";
    }

    private String infoCard(String rows) {
        return "<table width='100%' cellpadding='0' cellspacing='0' style='background:" + BG_COLOR + ";" +
               "border-radius:8px;border:1px solid #e5e7eb;margin:0 0 24px;overflow:hidden'>" +
               rows + "</table>";
    }

    private String row(String label, String value) {
        return "<tr><td style='padding:10px 16px;border-bottom:1px solid #e5e7eb;color:" + TEXT_MUTED + ";font-size:0.85rem;width:40%'>" +
               label + "</td>" +
               "<td style='padding:10px 16px;border-bottom:1px solid #e5e7eb;font-size:0.9rem'>" + value + "</td></tr>";
    }

    private String section(String heading, String body) {
        return "<div style='margin:0 0 20px'>" +
               "<h3 style='margin:0 0 10px;font-size:1rem;color:" + TEXT_DARK + "'>" + heading + "</h3>" +
               body + "</div>";
    }

    private String listItems(List<String> items, String emoji) {
        if (items == null || items.isEmpty()) return "<p style='color:" + TEXT_MUTED + ";margin:0'>No items.</p>";
        StringBuilder sb = new StringBuilder("<ul style='margin:0;padding-left:20px;color:" + TEXT_DARK + ";line-height:1.8'>");
        for (String item : items) sb.append("<li>").append(item).append("</li>");
        sb.append("</ul>");
        return sb.toString();
    }

    private String ctaButton(String text, String url) {
        return "<div style='text-align:center;margin:24px 0'>" +
               "<a href='" + url + "' style='display:inline-block;background:" + BRAND_COLOR + ";color:#fff;" +
               "padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:600;font-size:0.95rem'>" +
               text + "</a></div>";
    }

    private String footer() {
        return "<hr style='border:none;border-top:1px solid #e5e7eb;margin:24px 0'>" +
               "<p style='margin:0;font-size:0.75rem;color:" + TEXT_MUTED + ";text-align:center'>" +
               "© 2025 ResumeAI. All rights reserved.<br>" +
               "You received this email because you have an account with ResumeAI." +
               "</p>";
    }

    private int parseScore(String score) {
        try { return Integer.parseInt(score.replaceAll("[^0-9]", "")); }
        catch (Exception e) { return 0; }
    }

    // ──────────────────────────────────────────────────────────────
    // Core send
    // ──────────────────────────────────────────────────────────────

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent → {} | Subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {} | Subject: {} | Error: {}", to, subject, e.getMessage());
        }
    }
}
