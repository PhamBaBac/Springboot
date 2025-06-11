package com.bacpham.kanban_service.configuration.batch.config;

import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.data.repository.CrudRepository;

public class GenericRepositoryItemWriter<T> extends RepositoryItemWriter<T> {

    public GenericRepositoryItemWriter(CrudRepository<T, ?> repository, String methodName) {
        setRepository(repository);
        setMethodName(methodName);
    }
}
