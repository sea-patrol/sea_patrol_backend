package ru.sea.patrol.repository;

import reactor.core.publisher.Mono;
import ru.sea.patrol.entity.UserEntity;

public interface UserRepository {

    Mono<UserEntity> findByUsername(String username);

    Mono<UserEntity> save(UserEntity userEntity);
}
