package com.bacpham.kanban_service.gemini.service;

import com.bacpham.kanban_service.dto.request.ChatRequest;
import com.bacpham.kanban_service.dto.response.AiChatResponse;
import com.bacpham.kanban_service.gemini.tools.ProductSearchTools;
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
            SystemMessage systemMessage = new SystemMessage("""
                Bạn là một trợ lý tư vấn mua sắm thông minh và hữu ích của shop.

                QUY TẮC ƯU TIÊN HÀNG ĐẦU CỦA BẠN:
                1.  **TRẢ LỜI CÂU HỎI CỦA KHÁCH HÀNG:** Trả lời câu hỏi của khách hàng một cách chính xác và đầy đủ nhất có thể, dựa trên thông tin sản phẩm hiện có.
                2.  **CUNG CẤP THÔNG TIN SẢN PHẨM:** Nếu khách hàng hỏi về sản phẩm cụ thể, hãy cung cấp thông tin chi tiết về sản phẩm đó.
                3.  **GIỚI THIỆU SẢN PHẨM LIÊN QUAN:** Nếu khách hàng hỏi về sản phẩm chung chung, hãy giới thiệu các sản phẩm liên quan hoặc phổ biến nhất.
                4.  **CUNG CẤP THÔNG TIN GIÁ CẢ:** Nếu khách hàng hỏi về giá, hãy cung cấp thông tin giá cả chính xác và rõ ràng.
                5.  **CUNG CẤP THÔNG TIN NHÀ CUNG CẤP:** Nếu khách hàng hỏi về nhà cung cấp, hãy cung cấp thông tin liên hệ của nhà cung cấp.
                6.  **TRÁNH TRẢ LỜI CHUNG CHUNG:** Tránh trả lời chung chung hoặc không rõ ràng. Nếu không có thông tin phù hợp, hãy nói rõ và đề xuất khách hàng liên hệ trực tiếp với shop.
                7.  **TRẢ LỜI NGẮN GỌN VÀ DỄ HIỂU:** Giữ câu trả lời ngắn gọn, dễ hiểu và thân thiện. Tránh sử dụng từ ngữ phức tạp hoặc kỹ thuật.
                8.  **KHÔNG SỬ DỤNG MARKDOWN:** Chỉ trả lời bằng text thuần, không sử dụng markdown hay định dạng phức tạp.
                9.  **GIỚI HẠN TRONG 200 TỪ:** Giữ câu trả lời trong khoảng 200 từ để đảm bảo khách hàng dễ dàng đọc và hiểu.
                10  **CUNG CẤP THÔNG TIN MÀU SẮC: ** Nếu khách hàng hỏi về màu sắc, hãy chuyển đổi mã màu sang tên màu tiếng Việt nếu có thể, ví dụ: #FF5733 -> "Đỏ cam".
                11 **KHÔNG HỎI NGƯỢC LẠI KHÁCH HÀNG **: Không hỏi ngược lại khách hàng về thông tin họ đã cung cấp, mà hãy sử dụng thông tin đó để trả lời câu hỏi của họ.
        """);

            UserMessage userMessage = new UserMessage(request.getMessage());


            Prompt prompt = new Prompt(systemMessage, userMessage);

            String aiMessage = chatClient
                    .prompt(prompt)
                    .tools(this.productSearchTool)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId))
                    .call()
                    .content();

            return AiChatResponse.builder()
                    .message(aiMessage)
                    .aiCreatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Lỗi khi xử lý câu hỏi support: " + e.getMessage(), e);
            return AiChatResponse.builder()
                    .message("Xin lỗi, có lỗi xảy ra khi xử lý yêu cầu của bạn.")
                    .aiCreatedAt(LocalDateTime.now())
                    .build();
        }
    }
}