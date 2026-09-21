package com.bacpham.kanban_service;

import static com.bacpham.kanban_service.enums.Role.ADMIN;
import static com.bacpham.kanban_service.enums.Role.MANAGER;
import static com.bacpham.kanban_service.enums.Provider.LOCAL;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.repository.UserRepository;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableCaching
public class KanbanServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(KanbanServiceApplication.class, args);
	}

	@Bean
	@SuppressWarnings("null")
	public CommandLineRunner commandLineRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		return args -> {
			if (userRepository.findByEmail("admin@mail.com").isEmpty()) {
				User admin = User.builder()
						.firstname("Admin")
						.lastname("Admin")
						.email("admin@mail.com")
						.password(passwordEncoder.encode("password"))
						.role(ADMIN)
						.provider(LOCAL)
						.mfaEnabled(false)
						.build();
				userRepository.save(admin);
			}

			if (userRepository.findByEmail("manager@mail.com").isEmpty()) {
				User manager = User.builder()
						.firstname("Manager")
						.lastname("Manager")
						.email("manager@mail.com")
						.password(passwordEncoder.encode("password"))
						.role(MANAGER)
						.provider(LOCAL)
						.mfaEnabled(false)
						.build();
				userRepository.save(manager);
			}
		};
	}

}
