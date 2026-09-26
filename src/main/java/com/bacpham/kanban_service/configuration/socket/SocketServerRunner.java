package com.bacpham.kanban_service.configuration.socket;

import com.corundumstudio.socketio.SocketIOServer;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SocketServerRunner implements CommandLineRunner {

    private final SocketIOServer socketIOServer;

    @Override
    public void run(String... args) {
        try {
            socketIOServer.start();
            log.info(">>> Netty Socket.IO Server successfully started on port {} <<<",
                    socketIOServer.getConfiguration().getPort());
        } catch (Exception e) {
            log.error("Failed to start Netty Socket.IO server on port {}: {}",
                    socketIOServer.getConfiguration().getPort(), e.getMessage());
        }
    }

    @PreDestroy
    public void stopServer() {
        try {
            socketIOServer.stop();
            log.info("Netty Socket.IO Server stopped.");
        } catch (Exception e) {
            log.error("Error stopping Netty Socket.IO server: {}", e.getMessage());
        }
    }
}
