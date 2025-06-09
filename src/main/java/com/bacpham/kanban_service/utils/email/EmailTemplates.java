package com.bacpham.kanban_service.utils.email;


import lombok.Getter;

public enum EmailTemplates {
    EMAIL_CONFIRMATION("email-confirmation.html", "Xác minh đặt lại mã bảo mật (TFA)");
    ;

    @Getter
    private final String template;
    @Getter
    private final String subject;


    EmailTemplates(String template, String subject) {
        this.template = template;
        this.subject = subject;
    }
}
