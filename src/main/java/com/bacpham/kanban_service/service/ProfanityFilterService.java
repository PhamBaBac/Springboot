package com.bacpham.kanban_service.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProfanityFilterService {

    private static final List<Pattern> BANNED_PATTERNS = new ArrayList<>();

    static {
        // 1. Các từ viết tắt / tiếng lóng thô tục phổ biến (sử dụng boundary unicode để không match nhầm từ như admin, access, vlog...)
        String[] abbreviations = {
                "đm", "dm", "đcm", "dcm", "đkm", "dkm", "vcl", "vcll", "vl", "vkl",
                "clmm", "clgt", "cc", "vcc", "ccl", "cmn", "đmm", "dmm", "đb", "db"
        };
        for (String word : abbreviations) {
            BANNED_PATTERNS.add(Pattern.compile("(?i)(?<!\\p{L})" + Pattern.quote(word) + "(?!\\p{L})"));
        }

        // 2. Các từ thô tục tiếng Việt có dấu và không dấu
        String[] vulgarPhrases = {
                // Địt / Đụ
                "địt", "dit mẹ", "địt mẹ", "địt bà", "dit me", "dit ba", "đụ má", "du ma", "đụ mẹ", "du me", "đù má", "du me",
                // Lồn
                "lồn", "con lồn", "cái lồn", "hãm lồn", "xàm lồn", "mat lon", "mặt lồn", "con lon", "cai lon", "ham lon", "xam lon",
                // Cặc
                "cặc", "con cặc", "cái cặc", "thằng cặc", "con cac", "cai cac", "dau buoi", "đầu buồi",
                // Buồi
                "buồi", "buoi",
                // Đéo
                "đéo", "đéo cần", "đéo mua", "đéo dùng", "deo can", "deo mua",
                // Đĩ / Phò
                "con đĩ", "đĩ mẹ", "con di", "di me", "lũ đĩ", "phò", "con phò",
                // Chửi rủa / Lăng mạ
                "chó chết", "chó đẻ", "đồ chó", "con chó", "súc vật", "suc vat", "thằng khốn", "mất dạy",
                "mẹ mày", "bố mày", "cha mày", "bà mày", "ông cố nội mày",
                // Nội dung nhạy cảm / khiêu dâm
                "khiêu dâm", "phim sex", "gái gọi", "chat sex", "thủ dâm"
        };
        for (String phrase : vulgarPhrases) {
            BANNED_PATTERNS.add(Pattern.compile("(?i)(?<!\\p{L})" + Pattern.quote(phrase) + "(?!\\p{L})"));
        }

        // 3. Pattern phát hiện link spam / quảng cáo / website ngoài
        BANNED_PATTERNS.add(Pattern.compile("(?i)(https?://|www\\.)\\S+"));
        BANNED_PATTERNS.add(Pattern.compile("(?i)(?<!\\p{L})(t\\.me|zalo\\.me)/\\S+"));

        // 4. Pattern phát hiện kéo khách Zalo/Telegram
        BANNED_PATTERNS.add(Pattern.compile("(?i)(?<!\\p{L})(zalo|tele|telegram)\\s*[:.\\-]?\\s*0?\\d{9,10}(?!\\p{L})"));
    }

    /**
     * Kiểm tra nội dung có an toàn không
     */
    public boolean isSafe(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        return findViolatedWord(text).isEmpty();
    }

    /**
     * Tìm từ/cụm từ cấm xuất hiện đầu tiên trong văn bản (nếu có)
     */
    public Optional<String> findViolatedWord(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        String normalized = text.replaceAll("\\s+", " ").trim();

        for (Pattern pattern : BANNED_PATTERNS) {
            Matcher matcher = pattern.matcher(normalized);
            if (matcher.find()) {
                return Optional.of(matcher.group());
            }
        }

        return Optional.empty();
    }
}
