package com.bacpham.kanban_service.configuration.batch.config;

import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.core.io.FileSystemResource;

public class GenericFlatFileReader<T> extends FlatFileItemReader<T> {

    public GenericFlatFileReader(String[] fieldNames, Class<T> targetType, String filePath) {
        setResource(new FileSystemResource(filePath));
        setLinesToSkip(1);
        setName(targetType.getSimpleName() + "Reader");
        setLineMapper(createLineMapper(fieldNames, targetType));
    }

    private LineMapper<T> createLineMapper(String[] fieldNames, Class<T> targetType) {
        DefaultLineMapper<T> lineMapper = new DefaultLineMapper<>();

        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setDelimiter(",");
        tokenizer.setStrict(false);
        tokenizer.setNames(fieldNames);

        BeanWrapperFieldSetMapper<T> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(targetType);

        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);

        return lineMapper;
    }
}
