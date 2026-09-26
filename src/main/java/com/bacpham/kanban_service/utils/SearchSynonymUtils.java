package com.bacpham.kanban_service.utils;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

public class SearchSynonymUtils {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final List<List<String>> SYNONYM_GROUPS = List.of(
            List.of("ao polo", "ao coc tay", "ao phong", "ao thun", "ao thun coc tay", "ao ngan tay", "t-shirt", "polo"),
            List.of("ao so mi", "so mi", "ao dai tay", "so mi dai tay"),
            List.of("quan dai", "quan jean", "quan tay", "quan au", "jeans"),
            List.of("quan dui", "quan short", "quan ngo", "quan sot", "shorts"),
            List.of("ao khoac", "ao gio", "ao bomber", "ao hoodie", "ao cardigan", "hoodie", "cardigan"),
            List.of("dam", "vay", "chan vay", "dam xoe", "dam suong"),
            List.of("dep", "sandal", "giay dep", "tong", "dep le"),
            List.of("giay", "sneaker", "giay the thao", "giay luoi", "giay da")
    );

    /**
     * Bỏ dấu tiếng Việt và chuẩn hóa: "Áo cộc tay" -> "ao coc tay"
     */
    public static String removeDiacritics(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
        return withoutDiacritics.replace('đ', 'd').replace('Đ', 'D').trim().toLowerCase();
    }

    /**
     * Từ 1 keyword tìm kiếm ban đầu, tìm và mở rộng thêm các từ đồng nghĩa (alias).
     * Ví dụ: "ao coc tay" -> ["ao coc tay", "ao polo", "ao phong", "ao thun", "ao ngan tay", "polo"]
     */
    public static Set<String> expandKeywords(String rawKeyword) {
        Set<String> keywords = new LinkedHashSet<>();
        if (rawKeyword == null || rawKeyword.trim().isEmpty()) {
            return keywords;
        }

        String rawClean = rawKeyword.trim().toLowerCase();
        keywords.add(rawClean);

        String normalized = removeDiacritics(rawClean);
        if (!normalized.isEmpty()) {
            keywords.add(normalized);
        }

        for (List<String> group : SYNONYM_GROUPS) {
            boolean matched = false;
            for (String synonym : group) {
                if (rawClean.contains(synonym) || normalized.contains(synonym) ||
                    synonym.contains(rawClean) || synonym.contains(normalized)) {
                    matched = true;
                    break;
                }
            }

            if (matched) {
                keywords.addAll(group);
            }
        }

        return keywords;
    }
}
