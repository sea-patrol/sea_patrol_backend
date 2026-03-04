package ru.sea.patrol.user.repository;

import reactor.core.publisher.Mono;
import ru.sea.patrol.user.domain.UserEntity;

public interface UserRepository {

    Mono<UserEntity> findByUsername(String username);

    Mono<UserEntity> save(UserEntity userEntity);
}
