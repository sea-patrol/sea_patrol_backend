package ru.sea.patrol.mapper;

import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.sea.patrol.dto.UserDto;
import ru.sea.patrol.dto.UserRegistrationDto;
import ru.sea.patrol.entity.UserEntity;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto map(UserEntity entity);

    @Mapping(target = "role", constant = "USER")
    UserEntity map(UserRegistrationDto dto);

}
