package ru.sea.patrol.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.sea.patrol.dto.UserDto;
import ru.sea.patrol.dto.UserRegistrationDto;
import ru.sea.patrol.entity.UserEntity;
import ru.sea.patrol.mapper.UserMapper;
import ru.sea.patrol.repository.UserRepository;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserRepository userRepository;

    public Mono<UserDto> signup(UserRegistrationDto dto) {
        UserEntity entity = userMapper.map(dto);
        return userRepository.save(entity).map(userMapper::map).doOnSuccess(u -> {
            log.info("IN registerUser - user: {} created", u);
        });
    }

    public Mono<UserDto> getUserById(UUID id) {
        return userRepository.findById(id).map(userMapper::map);
    }

    public Mono<UserEntity> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}
