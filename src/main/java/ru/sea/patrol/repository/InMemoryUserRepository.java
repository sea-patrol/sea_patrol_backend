package ru.sea.patrol.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.sea.patrol.entity.UserEntity;
import ru.sea.patrol.entity.UserRole;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, UserEntity> users = new ConcurrentHashMap<>();

    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        UserEntity userEntity = UserEntity.builder()
                .username("user1")
                .password(passwordEncoder.encode("123456"))
                .email("email")
                .role(UserRole.USER)
                .locked(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        UserEntity userEntity2 = UserEntity.builder()
                .username("user2")
                .password(passwordEncoder.encode("123456"))
                .email("email")
                .role(UserRole.USER)
                .locked(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        UserEntity userEntity3 = UserEntity.builder()
                .username("user3")
                .password(passwordEncoder.encode("123456"))
                .email("email")
                .role(UserRole.USER)
                .locked(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        users.put(userEntity.getUsername(), userEntity);
        users.put(userEntity2.getUsername(), userEntity2);
        users.put(userEntity3.getUsername(), userEntity3);
    }

    @Override
    public Mono<UserEntity> save(UserEntity userEntity) {
        userEntity.setUpdatedAt(LocalDateTime.now());
        userEntity.setCreatedAt(LocalDateTime.now());
        users.put(userEntity.getUsername(), userEntity);
        return Mono.just(userEntity);
    }

    @Override
    public Mono<UserEntity> findByUsername(String username) {
        return Mono.justOrEmpty(users.get(username));
    }
}
