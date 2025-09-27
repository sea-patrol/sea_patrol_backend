package ru.sea.patrol.repository;

import ru.sea.patrol.entity.UserEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserRepository extends R2dbcRepository<UserEntity, UUID> {

    Mono<UserEntity> findByUsername(String username);
}
