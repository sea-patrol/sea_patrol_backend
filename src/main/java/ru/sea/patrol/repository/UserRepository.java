package ru.sea.patrol.repository;

import reactor.core.publisher.Mono;
import ru.sea.patrol.entity.UserEntity;

import java.util.UUID;

public interface UserRepository {

    Mono<UserEntity> findByUsername(String username);

    Mono<UserEntity> findById(UUID id);

    Mono<UserEntity> save(UserEntity userEntity);
}
