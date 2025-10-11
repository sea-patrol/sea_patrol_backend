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

    private final Map<UUID, UserEntity> usersWithIdKey = new ConcurrentHashMap<>();
    private final Map<String, UserEntity> usersWithNameKey = new ConcurrentHashMap<>();

    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        UserEntity userEntity = UserEntity.builder()
                .id(UUID.fromString("12345678-1234-1234-1234-123456789012"))
                .username("user1")
                .password(passwordEncoder.encode("password"))
                .email("email")
                .role(UserRole.USER)
                .locked(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        UserEntity userEntity2 = UserEntity.builder()
                .id(UUID.fromString("12345678-1234-1234-1234-123456789013"))
                .username("user2")
                .password(passwordEncoder.encode("password"))
                .email("email")
                .role(UserRole.USER)
                .locked(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        usersWithIdKey.put(userEntity.getId(), userEntity);
        usersWithNameKey.put(userEntity.getUsername(), userEntity);
        usersWithIdKey.put(userEntity2.getId(), userEntity2);
        usersWithNameKey.put(userEntity2.getUsername(), userEntity2);
    }

    @Override
    public Mono<UserEntity> findById(UUID id) {
        return Mono.just(usersWithIdKey.get(id));
    }

    @Override
    public Mono<UserEntity> save(UserEntity userEntity) {
        userEntity.setUpdatedAt(LocalDateTime.now());
        userEntity.setId(UUID.randomUUID());
        userEntity.setCreatedAt(LocalDateTime.now());
        usersWithIdKey.put(userEntity.getId(), userEntity);
        usersWithNameKey.put(userEntity.getUsername(), userEntity);
        return Mono.just(userEntity);
    }

    @Override
    public Flux<UserEntity> findAll() {
        return Flux.fromIterable(usersWithIdKey.values());
    }

    @Override
    public Mono<UserEntity> findByUsername(String username) {
        return Mono.justOrEmpty(usersWithNameKey.get(username));
    }
}
