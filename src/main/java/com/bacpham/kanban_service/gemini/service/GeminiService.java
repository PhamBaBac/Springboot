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
    private final String model;
    private final ProductServiceImpl productService;
    private final SubProductServiceImpl subProductService;
    private final SupplierServiceImpl supplierService;

    public GeminiService(@Qualifier("geminiRestClient") RestClient restClient,
                         @Value("${spring.ai.openai.api-key}") String geminiApiKey,
                         @Value("${spring.ai.openai.chat.options.model:gemini-3.8-flash}") String model,
                         ProductServiceImpl productService,
                         CategoryServiceImpl categoryService,
                         SubProductServiceImpl subProductService,
                         SupplierServiceImpl supplierService) {
        this.restClient = restClient;
        this.geminiApiKey = geminiApiKey;
        this.model = (model != null && !model.isBlank()) ? model : "gemini-2.5-flash";
        this.productService = productService;
        this.subProductService = subProductService;
        this.supplierService = supplierService;
    }

    public String generateContent(String textPrompt) {
        log.info("Sending text prompt to Gemini: '{}'", textPrompt);
        GeminiRequest requestBody = GeminiRequest.fromText(textPrompt);

        // Ưu tiên gemini-2.5-flash theo yêu cầu
        List<String> modelsToTry = new ArrayList<>();
        if (model != null && !model.isBlank()) {
            modelsToTry.add(model);
        }
        if (!modelsToTry.contains("gemini-2.5-flash")) modelsToTry.add("gemini-2.5-flash");
        if (!modelsToTry.contains("gemini-3.6-flash")) modelsToTry.add("gemini-3.6-flash");
        if (!modelsToTry.contains("gemini-3.8-flash")) modelsToTry.add("gemini-3.8-flash");
        if (!modelsToTry.contains("gemini-flash-latest")) modelsToTry.add("gemini-flash-latest");

        Exception lastException = null;
        for (String currentModel : modelsToTry) {
            try {
                String url = "/v1beta/models/%s:generateContent".formatted(currentModel);
                ResponseEntity<GeminiResponse> responseEntity = sendRequestToGemini(url, requestBody);
                String reply = extractTextFromResponse(responseEntity);
                if (reply != null && !reply.isBlank() && !reply.contains("Failed to get response")) {
                    log.info("Gemini reply successfully with model [{}]: {}", currentModel, reply);
                    return reply;
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("Model [{}] failed ({}). Trying next candidate model...", currentModel, e.getMessage());
            }
        }

        if (lastException != null) {
            throw new RuntimeException("Tất cả model Gemini đều báo lỗi: " + lastException.getMessage(), lastException);
        }
        return "Failed to get response from API.";
    }

    public String generateAutoContent(String type, String title, String context) {
        String safeTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : "sản phẩm";
        String extra = (context != null && !context.trim().isEmpty()) ? "Thông tin bổ sung: " + context.trim() : "";
        String prompt;

        String contentType = (type != null) ? type.trim().toLowerCase() : "general";

        switch (contentType) {
            case "product_description":
                prompt = String.format("""
                    You are an expert e-commerce copywriter.
                    Write a compelling, engaging, and high-converting product description for: "%s".
                    %s

                    STRICT REQUIREMENTS:
                    1. Output Language: Write entirely in natural, fluent, and modern Vietnamese (Tiếng Việt).
                    2. Length: 2 - 4 sentences (under 100 words).
                    3. Tone: Modern, friendly, professional, stimulating customer purchase intent.
                    4. Format: Plain text only. Absolutely NO markdown (no **, ##, bullet points), and DO NOT include introductory labels like "Mô tả sản phẩm:".
                    """, safeTitle, extra);
                break;

            case "product_content":
                prompt = String.format("""
                    You are an expert e-commerce content marketing specialist.
                    Write a comprehensive, engaging product introduction article in clean HTML for: "%s".
                    %s

                    STRICT REQUIREMENTS:
                    1. Output Language: Write all text in natural, fluent, and professional Vietnamese (Tiếng Việt).
                    2. Format: Return clean, valid HTML using ONLY the tags: <h3>, <p>, <ul>, <li>, <strong>.
                    3. Article Structure:
                       - <h3>Giới thiệu chung</h3>: A concise introductory paragraph about the product.
                       - <h3>Đặc điểm nổi bật</h3>: An unordered list <ul><li> detailing key features, materials, and benefits.
                       - <h3>Lợi ích khi sở hữu</h3>: A concise paragraph highlighting customer value.
                       - <h3>Hướng dẫn sử dụng & Bảo quản</h3>: Brief, practical usage and care guidelines.
                    4. Important: DO NOT wrap output in markdown blocks (no ```html or ```). Output raw HTML tags directly for TinyMCE editor.
                    """, safeTitle, extra);
                break;

            case "category_description":
                prompt = String.format("""
                    You are an expert e-commerce category manager.
                    Write a concise, professional, and appealing category description for: "%s".
                    %s

                    STRICT REQUIREMENTS:
                    1. Output Language: Write entirely in natural, fluent Vietnamese (Tiếng Việt).
                    2. Length: 1 - 3 sentences (under 80 words).
                    3. Content: Emphasize the quality, variety, and reliability of products in this category.
                    4. Format: Plain text only. Absolutely NO markdown, NO preamble or boilerplate labels.
                    """, safeTitle, extra);
                break;

            case "promotion_description":
                prompt = String.format("""
                    You are an expert retail marketing and promotion copywriter.
                    Write an exciting, attractive promotional campaign description with a strong call-to-action for: "%s".
                    %s

                    STRICT REQUIREMENTS:
                    1. Output Language: Write entirely in lively, enthusiastic Vietnamese (Tiếng Việt).
                    2. Length: 2 - 3 sentences (under 80 words).
                    3. Tone: Vibrant, urgent, high-energy, creating excitement and prompting quick checkout.
                    4. Format: Plain text only. Absolutely NO markdown, NO boilerplate labels.
                    """, safeTitle, extra);
                break;

            default:
                prompt = String.format("""
                    You are an expert copywriter. Write a professional, appealing description for: "%s".
                    %s

                    STRICT REQUIREMENTS:
                    1. Output Language: Natural, fluent Vietnamese (Tiếng Việt).
                    2. Format: Plain text only, no markdown, no unnecessary boilerplate.
                    """, safeTitle, extra);
                break;
        }

        try {
            String result = generateContent(prompt);
            if (result != null && !result.isBlank() && !result.contains("Failed to get response")) {
                result = result.replaceAll("^```(?:html)?\\s*", "").replaceAll("\\s*```$", "").trim();
                return result;
            }
        } catch (Exception e) {
            log.error("AI generateAutoContent thất bại ({}), kích hoạt nội dung mẫu dự phòng...", e.getMessage());
        }

        // Dự phòng thông minh nếu tất cả quota hoặc network gặp sự cố
        return buildFallbackContent(contentType, safeTitle);
    }

    private String buildFallbackContent(String contentType, String safeTitle) {
        return switch (contentType) {
            case "category_description" ->
                    String.format("Khám phá bộ sưu tập %s phong phú, chất lượng cao và chính hãng với mức giá ưu đãi nhất tại cửa hàng của chúng tôi.", safeTitle);
            case "product_description" ->
                    String.format("Sản phẩm %s chính hãng với thiết kế hiện đại, độ bền vượt trội và tiện ích tối đa cho nhu cầu của bạn.", safeTitle);
            case "promotion_description" ->
                    String.format("Ưu đãi đặc biệt cho %s diễn ra trong thời gian có hạn! Nhanh tay săn ngay để nhận giá tốt nhất hôm nay.", safeTitle);
            case "product_content" -> String.format("""
                    <h3>Giới thiệu sản phẩm</h3>
                    <p><strong>%s</strong> là sản phẩm chất lượng cao được thiết kế nhằm đáp ứng tối ưu nhu cầu của khách hàng.</p>
                    <h3>Đặc điểm nổi bật</h3>
                    <ul>
                      <li>Thiết kế hiện đại, tiện dụng và dễ dàng sử dụng.</li>
                      <li>Chất lượng bền đẹp, an toàn và đáng tin cậy.</li>
                      <li>Phù hợp với nhiều mục đích và không gian sử dụng.</li>
                    </ul>
                    <h3>Hướng dẫn bảo quản</h3>
                    <p>Bảo quản nơi khô ráo, thoáng mát, tránh ánh nắng trực tiếp và nhiệt độ cao.</p>
                    """, safeTitle);
            default ->
                    String.format("Thông tin mô tả chi tiết và chính hãng dành cho %s.", safeTitle);
        };
    }


    public String generateContent(String textPrompt, MultipartFile imageFile) throws IOException {
        log.info("Sending prompt '{}' and image '{}' to Gemini", textPrompt, imageFile.getOriginalFilename());

        String base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
        String mimeType = imageFile.getContentType();

        List<Part> parts = new ArrayList<>();
        parts.add(Part.fromText(textPrompt));
        parts.add(Part.fromImage(mimeType, base64Image));

        GeminiImageRequest requestBody = GeminiImageRequest.fromParts(parts);
        List<String> modelsToTry = List.of("gemini-2.5-flash", "gemini-3.6-flash", "gemini-3.8-flash", "gemini-flash-latest");
        for (String currentModel : modelsToTry) {
            try {
                String url = "/v1beta/models/%s:generateContent".formatted(currentModel);
                ResponseEntity<GeminiResponse> responseEntity = sendRequestToGemini(url, requestBody);
                String reply = extractTextFromResponse(responseEntity);
                if (reply != null && !reply.isBlank() && !reply.contains("Failed to get response")) {
                    log.info("Gemini multimodal reply: {}", reply);
                    return reply;
                }
            } catch (Exception e) {
                log.warn("Multimodal with model [{}] failed: {}. Trying next...", currentModel, e.getMessage());
            }
        }
        return "Failed to get response from API.";
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
                                    .append(", Số lượng: ").append(sub.getSize())
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
            You are a helpful and polite e-commerce AI customer support assistant.
            Here is the store information regarding products and suppliers:

            %s

            Customer question: "%s"

            STRICT INSTRUCTIONS:
            1. Output Language: Respond in natural, polite, and friendly Vietnamese (Tiếng Việt).
            2. Product inquiries: If the customer asks about specific products, recommend matching items with accurate details.
            3. Price inquiries: Provide accurate price information based on the data above.
            4. Supplier inquiries: Provide supplier contact details when asked.
            5. Missing info: If information is not available in the context, politely state so and suggest contacting the shop directly.
            6. Format: Plain text only, NO markdown.
            7. Length: Concise and clear (maximum 200 words).
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