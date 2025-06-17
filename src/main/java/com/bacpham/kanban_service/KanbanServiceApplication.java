package com.bacpham.kanban_service;

import com.bacpham.kanban_service.dto.request.RegisterRequest;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.impl.AuthenticationServiceImpl;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import static com.bacpham.kanban_service.enums.Role.ADMIN;
import static com.bacpham.kanban_service.enums.Role.MANAGER;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class KanbanServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(KanbanServiceApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(AuthenticationServiceImpl service, UserRepository userRepository) {
		return args -> {
			if (userRepository.findByEmail("admin@mail.com").isEmpty()) {
				var admin = RegisterRequest.builder()
						.firstName("Admin")
						.lastName("Admin")
						.email("admin@mail.com")
						.password("password")
						.role(ADMIN)
						.build();
			}

			if (userRepository.findByEmail("manager@mail.com").isEmpty()) {
				var manager = RegisterRequest.builder()
						.firstName("Manager")
						.lastName("Manager")
						.email("manager@mail.com")
						.password("password")
						.role(MANAGER)
						.build();
			}
		};
	}


}
