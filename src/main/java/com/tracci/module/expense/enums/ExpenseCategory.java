package com.tracci.module.expense.enums;

import java.util.Arrays;

public enum ExpenseCategory {

    FOOD("food", "餐饮"),
    TRANSPORT("transport", "交通"),
    HOTEL("hotel", "住宿"),
    ATTRACTION("attraction", "景点"),
    SHOPPING("shopping", "购物"),
    OTHER("other", "其他");

    /** 自定义分类前缀：expenses.category 存 custom:<categoryId> */
    public static final String CUSTOM_PREFIX = "custom:";

    private final String value;
    private final String displayName;

    ExpenseCategory(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public String value() {
        return value;
    }

    public String displayName() {
        return displayName;
    }

    public static ExpenseCategory fromValue(String value) {
        return Arrays.stream(values())
                .filter(c -> c.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported expense category: " + value));
    }

    public static String displayNameOf(String category) {
        try {
            return fromValue(category).displayName();
        } catch (IllegalArgumentException ex) {
            return category;
        }
    }

    /** 是否为系统分类 */
    public static boolean isSystem(String category) {
        if (category == null) return false;
        return Arrays.stream(values()).anyMatch(c -> c.value.equals(category));
    }

    /** 是否为自定义分类（custom:<id> 格式） */
    public static boolean isCustom(String category) {
        return category != null && category.startsWith(CUSTOM_PREFIX);
    }

    /** 从 custom:<id> 中提取分类 ID */
    public static String customIdOf(String category) {
        if (!isCustom(category)) return null;
        return category.substring(CUSTOM_PREFIX.length());
    }
}
