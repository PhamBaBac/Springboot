package com.bacpham.kanban_service.gemini.service;

import com.bacpham.kanban_service.dto.response.AiChatResponse;
import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.dto.response.SubProductResponse;
import com.bacpham.kanban_service.dto.response.SupplierResponse;
import com.bacpham.kanban_service.gemini.dto.GeminiImageRequest;
import com.bacpham.kanban_service.gemini.dto.GeminiRequest;
import com.bacpham.kanban_service.gemini.dto.GeminiResponse;
import com.bacpham.kanban_service.gemini.dto.Part;
import com.bacpham.kanban_service.service.impl.CategoryServiceImpl;
import com.bacpham.kanban_service.service.impl.ProductServiceImpl;
import com.bacpham.kanban_service.service.impl.SubProductServiceImpl;
import com.bacpham.kanban_service.service.impl.SupplierServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GeminiService {

    private final RestClient restClient;
    private final String geminiApiKey;
    private static final String DEFAULT_MODEL = "gemini-1.5-flash-latest";
    private final ProductServiceImpl productService;
    private final SubProductServiceImpl subProductService;
    private final SupplierServiceImpl supplierService;
    public GeminiService(@Qualifier("geminiRestClient") RestClient restClient,
                         @Value("${spring.ai.openai.api-key}") String geminiApiKey, ProductServiceImpl productService, CategoryServiceImpl categoryService, SubProductServiceImpl subProductService, SupplierServiceImpl supplierService) {
        this.restClient = restClient;
        this.geminiApiKey = geminiApiKey;
        this.productService = productService;
        this.subProductService = subProductService;
        this.supplierService = supplierService;
    }
    public String generateContent(String textPrompt) {
        log.info("Sending text prompt to Gemini: '{}'", textPrompt);
        GeminiRequest requestBody = GeminiRequest.fromText(textPrompt);
        String url = "/v1beta/models/%s:generateContent".formatted(DEFAULT_MODEL);

        ResponseEntity<GeminiResponse> responseEntity = sendRequestToGemini(url, requestBody);

        String reply = extractTextFromResponse(responseEntity);
        log.info("Gemini reply: {}", reply);
        return reply;
    }


    public String generateContent(String textPrompt, MultipartFile imageFile) throws IOException {
        log.info("Sending prompt '{}' and image '{}' to Gemini", textPrompt, imageFile.getOriginalFilename());

        String base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
        String mimeType = imageFile.getContentType();

        List<Part> parts = new ArrayList<>();
        parts.add(Part.fromText(textPrompt));
        parts.add(Part.fromImage(mimeType, base64Image));

        GeminiImageRequest requestBody = GeminiImageRequest.fromParts(parts);
        String url = "/v1beta/models/%s:generateContent".formatted(DEFAULT_MODEL);

        ResponseEntity<GeminiResponse> responseEntity = sendRequestToGemini(url, requestBody);

        String reply = extractTextFromResponse(responseEntity);
        log.info("Gemini multimodal reply: {}", reply);
        return reply;
    }
    private ResponseEntity<GeminiResponse> sendRequestToGemini(String url, Object requestBody) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(url)
                        .queryParam("key", geminiApiKey)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toEntity(GeminiResponse.class);
    }

    private String extractTextFromResponse(ResponseEntity<GeminiResponse> responseEntity) {
        return responseEntity.getBody() != null ?
                responseEntity.getBody().getFirstCandidateText().orElse("No response text found.") :
                "Failed to get response from API.";
    }

    public AiChatResponse support(String message) {
        try {
            List<ProductResponse> products = productService.getProducts();
            List<SupplierResponse> suppliers = supplierService.findAllSupplier();

            if (products.isEmpty() && suppliers.isEmpty()) {
                return AiChatResponse.builder()
                        .message("Xin lỗi, hiện tại chưa có thông tin sản phẩm hoặc nhà cung cấp. Vui lòng thử lại sau.")
                        .aiCreatedAt(LocalDateTime.now())
                        .build();
            }

            StringBuilder context = new StringBuilder();

            if (!products.isEmpty()) {
                context.append("=== DANH SÁCH SẢN PHẨM ===\n");
                for (ProductResponse product : products) {
                    context.append("Sản phẩm: ").append(product.getTitle()).append("\n");
                    context.append("Mô tả: ").append(product.getDescription()).append("\n");

                    if (product.getCategories() != null && !product.getCategories().isEmpty()) {
                        String categories = product.getCategories().stream()
                                .map(category -> category.getTitle())
                                .collect(Collectors.joining(", "));
                        context.append("Danh mục: ").append(categories).append("\n");
                    }

                    List<SubProductResponse> subProducts = subProductService.getAllSubProduct(product.getId());
                    if (!subProducts.isEmpty()) {
                        context.append("Các biến thể:\n");
                        for (SubProductResponse sub : subProducts) {
                            context.append("  - Size: ").append(sub.getSize())
                                    .append(", Màu: ").append(sub.getColor())
                                    .append(", Giá: ").append(formatPrice(sub.getPrice()))
                                    .append(", Số lượng: ").append(sub.getQty())
                                    .append("\n");
                        }
                    }
                    context.append("\n");
                }
            }

            if (!suppliers.isEmpty()) {
                context.append("=== THÔNG TIN NHÀ CUNG CẤP ===\n");
                for (SupplierResponse supplier : suppliers) {
                    context.append("Tên: ").append(supplier.getName()).append("\n");
                    context.append("Email: ").append(supplier.getEmail()).append("\n\n");
                }
            }

            String prompt = String.format("""
            Bạn là trợ lý tư vấn sản phẩm thương mại điện tử. Dưới đây là thông tin chi tiết về sản phẩm và nhà cung cấp:

            %s

            Câu hỏi của khách hàng: "%s"

            Hướng dẫn trả lời:
            1. Trả lời bằng tiếng Việt, tự nhiên và thân thiện
            2. Nếu khách hỏi về sản phẩm cụ thể, hãy tìm và giới thiệu sản phẩm phù hợp
            3. Nếu khách hỏi về giá, hãy cung cấp thông tin giá cả chính xác
            4. Nếu khách hỏi về nhà cung cấp, hãy cung cấp thông tin liên hệ
            5. Nếu không có thông tin phù hợp, hãy nói rõ và đề xuất liên hệ trực tiếp
            6. Không sử dụng markdown, chỉ trả lời bằng text thuần
            7. Giữ câu trả lời ngắn gọn, dễ hiểu (tối đa 200 từ)
            """, context.toString(), message);

            String aiMessage = generateContent(prompt);

            return AiChatResponse.builder()
                    .message(aiMessage)
                    .aiCreatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Lỗi khi xử lý câu hỏi support: " + e.getMessage(), e);
            return AiChatResponse.builder()
                    .message("Xin lỗi, có lỗi xảy ra khi xử lý câu hỏi của bạn. Vui lòng thử lại sau hoặc liên hệ trực tiếp với chúng tôi.")
                    .aiCreatedAt(LocalDateTime.now())
                    .build();
        }
    }


    private String formatPrice(Double price) {
        if (price == null) return "Chưa có giá";
        return String.format("%,.0f VNĐ", price);
    }


}