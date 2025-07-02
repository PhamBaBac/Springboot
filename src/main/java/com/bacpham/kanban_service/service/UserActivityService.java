package com.bacpham.kanban_service.service;


import com.bacpham.kanban_service.dto.request.UserActiveRequest;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.entity.UserActivity;
import com.bacpham.kanban_service.enums.ActionType;
import com.bacpham.kanban_service.repository.UserActivityRepository;
import com.bacpham.kanban_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserActivityRepository userActivityRepository;
    private final UserRepository userRepository;

    public void recordViewProductActivity(UserActiveRequest userActiveRequest) {
        String userId = userActiveRequest.getUserId();
        String productId = userActiveRequest.getProductId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        UserActivity activity = UserActivity.builder()
                .user(user)
                .actionType(ActionType.VIEW_PRODUCT)
                .entityId(productId)
                .build();
        userActivityRepository.save(activity);
    }
}
