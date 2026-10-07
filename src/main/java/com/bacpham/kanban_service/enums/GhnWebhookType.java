package com.bacpham.kanban_service.enums;

import lombok.Getter;

@Getter
public enum GhnWebhookType {
    CREATE("create"),
    SWITCH_STATUS("switch_status"),
    UPDATE_WEIGHT("update_weight"),
    UPDATE_COD("update_cod"),
    UPDATE_FEE("update_fee"),
    UPDATE_PAYMENT_TYPE("update_payment_type"),
    COD("cod"),
    UPDATE_PARTIAL_RETURN("update_partial_return"),
    UNKNOWN("unknown");

    private final String value;

    GhnWebhookType(String value) {
        this.value = value;
    }

    public static GhnWebhookType fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (GhnWebhookType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
