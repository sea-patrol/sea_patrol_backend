package ru.sea.patrol.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@RestController
public class ReactAppController {

    @GetMapping(value = {"/", "/game"})
    public Mono<ResponseEntity<Resource>> index() {
        return Mono.just(
                ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(new ClassPathResource("static/index.html"))
        );
    }
}
