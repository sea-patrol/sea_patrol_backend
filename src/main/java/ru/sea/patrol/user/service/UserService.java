package ru.sea.patrol.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.sea.patrol.auth.api.dto.UserDto;
import ru.sea.patrol.auth.api.dto.UserRegistrationDto;
import ru.sea.patrol.user.domain.UserEntity;
import ru.sea.patrol.user.mapper.UserMapper;
import ru.sea.patrol.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserRepository userRepository;

    public Mono<UserDto> create(UserRegistrationDto dto) {
        UserEntity entity = userMapper.map(dto);
        return userRepository.save(entity).map(userMapper::map).doOnSuccess(u -> {
            log.info("IN registerUser - user: {} created", u);
        });
    }

    public Mono<UserEntity> retrieve(String username) {
        return userRepository.findByUsername(username);
    }
}
