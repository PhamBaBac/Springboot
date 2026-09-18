package com.bacpham.kanban_service.service;


import com.bacpham.kanban_service.dto.request.UserActiveRequest;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.entity.UserActivity;
import com.bacpham.kanban_service.enums.ActionType;
import com.bacpham.kanban_service.repository.UserActivityRepository;
import com.bacpham.kanban_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserActivityRepository userActivityRepository;
    private final UserRepository userRepository;

    /**
     * Fix Async A-3: Chuyển ghi nhận hoạt động người dùng sang background thread pool.
     * Request xem sản phẩm của User được phản hồi ngay lập tức, không bị chậm vì DB write.
     */
    @Async("taskExecutor")
    public void recordViewProductActivity(UserActiveRequest userActiveRequest) {
        if (userActiveRequest == null || userActiveRequest.getUserId() == null) {
            return;
        }
        try {
            String userId = userActiveRequest.getUserId();
            String productId = userActiveRequest.getProductId();
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("UserActivity: User not found with id: {}", userId);
                return;
            }
            UserActivity activity = UserActivity.builder()
                    .user(user)
                    .actionType(ActionType.VIEW_PRODUCT)
                    .entityId(productId)
                    .build();
            userActivityRepository.save(activity);
            log.debug("Recorded view product activity asynchronously for user: {}, product: {}", userId, productId);
        } catch (Exception e) {
            log.warn("Failed to record view product activity: {}", e.getMessage());
        }
    }
}
