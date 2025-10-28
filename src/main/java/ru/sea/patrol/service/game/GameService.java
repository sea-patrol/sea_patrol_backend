package ru.sea.patrol.service.game;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import ru.sea.patrol.service.MessageService;
import ru.sea.patrol.service.chat.ChatUser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class GameService implements MessageService {

  private final Map<String, Player> players = new ConcurrentHashMap<>();

  @Override
  public Flux<WebSocketMessage> initialize(String username, WebSocketSession session) {
    var player = new Player().setName(username);
    return null;
  }

  private Player retrievePlayer(String username) {
    return players.computeIfAbsent(username, key -> { return new Player().setName(key); });
  }
}
