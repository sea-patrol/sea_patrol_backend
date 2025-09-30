package ru.sea.patrol.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import ru.sea.patrol.service.ChatService;

@Component
public class ChatHandler implements WebSocketHandler {

  @Autowired private ChatService chatService;

  @Override
  public Mono<Void> handle(WebSocketSession session) {
    return chatService.handle(session);
  }
}
