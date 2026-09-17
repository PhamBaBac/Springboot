package com.bacpham.kanban_service.gemini.service;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import com.bacpham.kanban_service.dto.response.ModerationResult;

@Service
public class ModerationCommentService {
    private final ChatClient chatClient;

    public ModerationCommentService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .build();
    }

    public boolean isCommentSafe(String comment, List<String> imageUrls) {
        String systemText = """
            Bạn là một hệ thống kiểm duyệt nội dung nghiêm ngặt.
            Nhiệm vụ của bạn là phân tích bình luận (bao gồm cả văn bản và hình ảnh) của người dùng.
            Trả về một đối tượng JSON với một trường duy nhất là "safe" (kiểu boolean).
            
            Các nội dung sau được coi là KHÔNG AN TOÀN (safe: false):
            - Lời lẽ xúc phạm, chửi thề, lăng mạ trong văn bản.
            - Hình ảnh hoặc văn bản chứa nội dung khiêu dâm, bạo lực, ghê rợn.
            - Hình ảnh hoặc văn bản có tính chất thù ghét, phân biệt đối xử.
            - Spam, quảng cáo, lừa đảo trong văn bản hoặc hình ảnh.
            - Hình ảnh hoặc văn bản tiết lộ thông tin cá nhân nhạy cảm.
            
            Nếu nội dung không vi phạm, hãy coi nó là AN TOÀN (safe: true).
        """;

        List<Media> imageMedia = new ArrayList<>();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            for (String url : imageUrls) {
                try {
                    imageMedia.add(new Media(MimeTypeUtils.IMAGE_JPEG, new UrlResource(url)));
                } catch (MalformedURLException e) {
                    System.err.println("URL hình ảnh không hợp lệ: " + url);
                }
            }
        }

        try {
            ModerationResult result = chatClient.prompt()
                    .system(systemText)
                    .user(userSpec -> {
                        userSpec.text(comment); // Thêm phần text
                        if (!imageMedia.isEmpty()) {
                            userSpec.media(imageMedia.toArray(new Media[0])); // Thêm các ảnh
                        }
                    })
                    .call()
                    .entity(ModerationResult.class);

            return result.safe();

        } catch (Exception e) {
            System.err.println("Lỗi khi gọi AI để kiểm duyệt: " + e.getMessage());
            return false;
        }
    }
}
