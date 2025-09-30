package ru.sea.patrol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import ru.sea.patrol.dto.ChatMessageEvent;

@Slf4j
@Service
public class ChatService {

    private final Sinks.Many<ChatMessageEvent> messageSink = Sinks.many().replay().limit(10);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Mono<Void> handle(WebSocketSession session) {
        // Извлекаем Authentication реактивно
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication().getName())
                .flatMap(username -> {
                    log.info("Подключился пользователь: " + username);

                    Flux<ChatMessageEvent> output = messageSink.asFlux();

                    Mono<Void> input = session.receive()
                            .map(buffer -> {
                                try {
                                    var msg = objectMapper.readValue(buffer.getPayloadAsText(), ChatMessageEvent.class);
                                    log.info("Получено сообщение от пользователя: " + username + ": " + msg.getContent());
                                    return msg;
                                } catch (Exception e) {
                                    return new ChatMessageEvent("System", "Error parsing message");
                                }
                            })
                            .doOnNext(msg -> {
                                // Отправляем сообщение с именем пользователя
                                messageSink.tryEmitNext(new ChatMessageEvent(username, msg.getContent()));
                            })
                            .then();

                    return session.send(
                            output.map(msg -> session.textMessage(msg.toString()))
                    ).and(input);
                })
                .then();
    }
}
