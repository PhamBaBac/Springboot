package com.bacpham.kanban_service.configuration.batch.product;

import com.bacpham.kanban_service.configuration.batch.config.GenericBatchJobFactory;
import com.bacpham.kanban_service.configuration.batch.config.GenericFlatFileReader;
import com.bacpham.kanban_service.configuration.batch.config.GenericRepositoryItemWriter;
import com.bacpham.kanban_service.dto.request.ProductCreationRequestCSV;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.mapper.ProductMapper;
import com.bacpham.kanban_service.repository.CategoryRepository;
import com.bacpham.kanban_service.repository.ProductRepository;
import com.bacpham.kanban_service.repository.SupplierRepository;
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
public class ProductJobConfig {
    private final GenericBatchJobFactory factory;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final SupplierRepository supplierRepository;
    private final CategoryRepository categoryRepository;

    @Bean
    public FlatFileItemReader<ProductCreationRequestCSV> productReader() {
        return new GenericFlatFileReader<>(
                new String[]{"title", "slug", "description", "content", "categoryNames", "supplierName", "images"},
                ProductCreationRequestCSV.class,
                "src/main/resources/categories.csv"
        );
    }

    @Bean
    public ItemProcessor<ProductCreationRequestCSV, Product> productProcessor() {
        return requestCSV -> productMapper.toProductFromCSV(
                requestCSV,
                supplierRepository,
                categoryRepository
        );
    }

    @Bean
    public ItemWriter<Product> productWriter() {
        return new GenericRepositoryItemWriter<>(productRepository, "save");
    }

    @Bean
    public Step productStep() {
        return factory.createStep("productStep", productReader(), productProcessor(), productWriter(), 1000);
    }

    @Bean
    public Job productJob() {
        return factory.createJob("productJob", productStep());
    }
}
