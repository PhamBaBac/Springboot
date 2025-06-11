package com.bacpham.kanban_service.configuration.batch.category;

import com.bacpham.kanban_service.configuration.batch.config.GenericBatchJobFactory;
import com.bacpham.kanban_service.configuration.batch.config.GenericFlatFileReader;
import com.bacpham.kanban_service.configuration.batch.config.GenericRepositoryItemWriter;
import com.bacpham.kanban_service.dto.request.CategoryRequest;
import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.mapper.CategoryMapper;
import com.bacpham.kanban_service.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CategoryJobConfig {

    private final GenericBatchJobFactory factory;
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Bean
    public FlatFileItemReader<CategoryRequest> categoryReader() {
        return new GenericFlatFileReader<>(
                new String[]{"id", "title", "slug", "description", "parentId"},
                CategoryRequest.class,
                "src/main/resources/categories.csv"
        );
    }

    @Bean
    public ItemProcessor<CategoryRequest, Category> categoryProcessor() {
        return categoryMapper::toCategory;
    }

    @Bean
    public ItemWriter<Category> categoryWriter() {
        return new GenericRepositoryItemWriter<>(categoryRepository, "save");
    }

    @Bean
    public Step categoryStep() {
        return factory.createStep("categoryStep", categoryReader(), categoryProcessor(), categoryWriter(), 1000);
    }

    @Bean
    public Job categoryJob() {
        return factory.createJob("categoryJob", categoryStep());
    }
}
