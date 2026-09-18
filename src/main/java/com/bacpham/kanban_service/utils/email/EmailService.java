package com.bacpham.kanban_service.utils.email;

import static com.bacpham.kanban_service.utils.email.EmailTemplates.EMAIL_CONFIRMATION;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    @Value("${spring.mail.username}")
    String sender;
    @Async("taskExecutor")
    public void sendVerificationCodeEmail(
            String destinationEmail, String code
    ) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, UTF_8.name());
            messageHelper.setFrom(sender);

            final String templateName = EMAIL_CONFIRMATION.getTemplate();

            Map<String, Object> variables = new HashMap<>();
            variables.put("code", code);

            Context context = new Context();
            context.setVariables(variables);
            messageHelper.setSubject(EMAIL_CONFIRMATION.getSubject());

            String htmlTemplate = templateEngine.process(templateName, context);
            messageHelper.setText(htmlTemplate, true);

            messageHelper.setTo(destinationEmail);
            mailSender.send(mimeMessage);
            log.info("INFO - Email successfully sent to {} with template {}", destinationEmail, templateName);
        } catch (Exception e) {
            log.warn("WARNING - Cannot send Email to {}: {}", destinationEmail, e.getMessage());
        }
    }
}


