package com.bacpham.kanban_service.gemini.service;

import com.bacpham.kanban_service.helper.exception.CommentProfanityException;
import com.bacpham.kanban_service.service.ProfanityFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewModerationService {

    private final ProfanityFilterService profanityFilterService;

    public boolean isReviewApproved(String comment, List<String> base64Images) {
        return profanityFilterService.isSafe(comment);
    }

    public void validateReview(String comment, List<String> base64Images) {
        profanityFilterService.findViolatedWord(comment).ifPresent(violatedWord -> {
            log.warn("Review rejected by regex profanity filter: '{}' in '{}'", violatedWord, comment);
            throw new CommentProfanityException(violatedWord);
        });
    }
}
