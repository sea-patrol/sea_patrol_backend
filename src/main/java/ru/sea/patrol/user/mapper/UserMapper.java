package ru.sea.patrol.user.mapper;

import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import ru.sea.patrol.auth.api.dto.UserDto;
import ru.sea.patrol.auth.api.dto.UserRegistrationDto;
import ru.sea.patrol.user.domain.UserEntity;
import org.mapstruct.Mapper;
import ru.sea.patrol.auth.security.CustomPasswordEncoder;

@Mapper(componentModel = "spring")
public abstract class UserMapper {

    @Autowired
    private CustomPasswordEncoder passwordEncoder;

    public abstract UserDto map(UserEntity entity);

    @Mapping(target = "role", constant = "USER")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "locked", constant = "false")
    @Mapping(target = "password", source = "password", qualifiedByName = "passwordEncoder")
    public abstract UserEntity map(UserRegistrationDto dto);

    @Named("passwordEncoder")
    protected String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

}
