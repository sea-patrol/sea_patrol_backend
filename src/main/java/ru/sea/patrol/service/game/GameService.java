package ru.sea.patrol.service.game;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import ru.sea.patrol.dto.websocket.MessageOutput;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class GameService {

  private final Map<String, Player> players = new ConcurrentHashMap<>();
  private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

  public Flux<MessageOutput> initialize(String username) {
    var player = retrievePlayer(username);
    return player.getSink().asFlux();
  }

  public void cleanupPlayer(String username) {
    var user = players.remove(username);
  }

  private Player retrievePlayer(String username) {
    return players.computeIfAbsent(username, name -> { return
            new Player(name)
              .setModel("model")
              .setMaxHealth(500)
              .setHealth(500)
              .setAngle(0)
              .setVelocity(0)
              .setX(0)
              .setZ(0)
              .setHeight(1.5f)
              .setWidth(2f)
              .setLength(8f);
    });
  }
}
