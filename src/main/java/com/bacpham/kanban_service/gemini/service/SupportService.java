package com.bacpham.kanban_service.gemini.service;

import com.bacpham.kanban_service.dto.request.ChatRequest;
import com.bacpham.kanban_service.dto.response.AiChatResponse;
import com.bacpham.kanban_service.gemini.tools.ProductSearchTools;
import com.bacpham.kanban_service.dto.response.ProductResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SupportService {

    private final ChatClient chatClient;
    private final JdbcChatMemoryRepository jdbcChatMemoryRepository;
    private static final Logger log = LoggerFactory.getLogger(SupportService.class);
    private final ProductSearchTools productSearchTool;

    public SupportService(ChatClient.Builder chatClientBuilder, ProductSearchTools productSearchTool,
                          JdbcChatMemoryRepository jdbcChatMemoryRepository) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(20)
                .build();
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.productSearchTool = productSearchTool;
        this.jdbcChatMemoryRepository = jdbcChatMemoryRepository;


    }
    public AiChatResponse supportWithHistory(ChatRequest request, String userId) {
        try {
            ProductSearchTools.clearFoundProducts();

            SystemMessage systemMessage = new SystemMessage("""
                Bạn là một chuyên viên tư vấn bán hàng thông minh, thân thiện và nhiệt tình của cửa hàng.

                QUY TẮC TƯ VẤN:
                1. KHI KHÁCH HÀNG HỎI HOẶC TÌM KIẾM SẢN PHẨM: Luôn gọi công cụ `productSearchTool` để tra cứu sản phẩm thực tế trong kho:
                   - Trích xuất tên sản phẩm, thương hiệu hoặc từ khóa chính vào `keyword` (ví dụ: tên máy, đồ dùng, thiết bị, loại hàng).
                   - Nếu khách hỏi loại/danh mục sản phẩm, điền vào `categoryName` (ví dụ: "Nhà bếp", "Gia dụng", "Nội thất", "Điện tử").
                   - Nếu khách hỏi thông số/kích cỡ (size, dung tích, công suất...), điền vào `sizes`.
                   - Nếu khách hỏi màu sắc (đen, trắng, xám, bạc, đỏ, xanh...), điền vào `colors`.
                   - Nếu khách hỏi khoảng giá, điền vào `minPrice`, `maxPrice`.
                   - Mặc định lấy tối đa 5 - 6 sản phẩm tốt nhất.
                2. KHI TRẢ LỜI:
                   - Nếu tìm thấy sản phẩm: Giao diện chat sẽ tự động hiển thị thẻ sản phẩm trực quan (kèm ảnh sắc nét, giá bán, giảm giá và nút Mua hàng) ngay bên dưới câu trả lời của bạn. Do đó, bạn CHỈ CẦN phản hồi ngắn gọn, chào hỏi lịch sự và giới thiệu thân thiện 1 - 2 câu để mời khách xem các thẻ sản phẩm bên dưới. Tuyệt đối KHÔNG cần phải liệt kê lại danh sách sản phẩm dài dòng.
                   - Nếu không tìm thấy: Báo lịch sự là mẫu này hiện chưa có hoặc đã hết hàng, và gợi ý các mẫu khác đang có sẵn.
                   - Nếu khách hỏi về màu sắc có mã HEX (như #FF5733), hãy chuyển đổi sang tên màu tiếng Việt (ví dụ: Đỏ cam).
                3. PHONG CÁCH:
                   - Lịch sự, thân thiện, xưng hô "Shop" và "bạn".
                   - Trả lời ngắn gọn, súc tích (dưới 100 từ), dễ đọc.
                   - Trả lời bằng văn bản rõ ràng, không dùng các ký hiệu markdown phức tạp.
            """);

            UserMessage userMessage = new UserMessage(request.getMessage());

            Prompt prompt = new Prompt(systemMessage, userMessage);

            String aiMessage = chatClient
                    .prompt(prompt)
                    .tools(this.productSearchTool)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId))
                    .call()
                    .content();

            List<ProductResponse> products = ProductSearchTools.getAndClearFoundProducts();

            return AiChatResponse.builder()
                    .message(aiMessage)
                    .aiCreatedAt(LocalDateTime.now())
                    .products(products)
                    .build();

        } catch (Exception e) {
            log.error("Lỗi khi xử lý câu hỏi support: " + e.getMessage(), e);
            return AiChatResponse.builder()
                    .message("Xin lỗi, có lỗi xảy ra khi xử lý yêu cầu của bạn.")
                    .aiCreatedAt(LocalDateTime.now())
                    .build();
        } finally {
            ProductSearchTools.clearFoundProducts();
        }
    }
}