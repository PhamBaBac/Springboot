package com.bacpham.kanban_service.service;


import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.entity.UserActivity;
import com.bacpham.kanban_service.enums.ActionType;
import com.bacpham.kanban_service.repository.UserActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserActivityRepository userActivityRepository;

    public void recordViewProductActivity(User user, String productId) {
        UserActivity activity = UserActivity.builder()
                .user(user)
                .actionType(ActionType.VIEW_PRODUCT)
                .entityId(productId)
                .build();
        userActivityRepository.save(activity);
    }
}
