package ru.sea.patrol.service.game;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.sea.patrol.ws.protocol.MessageType;
import ru.sea.patrol.ws.protocol.dto.MessageInput;
import ru.sea.patrol.ws.protocol.dto.MessageOutput;
import ru.sea.patrol.ws.protocol.dto.PlayerInputMessage;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

  private final ObjectMapper objectMapper;
  private final Random random = new Random();

  private final Map<String, Player> players = new ConcurrentHashMap<>();
  private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

  public Flux<MessageOutput> initialize(String playerName) {
    var player = retrievePlayer(playerName);
    return player.getSink().asFlux();
  }

  public Mono<Void> handle(String username, MessageInput msg) {
    switch (msg.getType()) {
      case MessageType.PLAYER_INPUT:
        return handlePlayerInput(username, msg.getPayload());
      default:
        return Mono.empty();
    }
  }

  private Mono<Void> handlePlayerInput(String username, JsonNode payload) {
    try {
      PlayerInputMessage msg = objectMapper.treeToValue(payload, PlayerInputMessage.class);
      var player = players.get(username);
      if (player != null && player.getShip() != null) {
        log.info("Player {} input: {}", username, msg);
        player.getShip().setInput(msg);
      }
      return Mono.empty();
    } catch (Exception e) {
      return Mono.error(e);
    }
  }

  public void startRoom(String roomName) {
    var room = rooms.computeIfAbsent(roomName, GameRoom::new);
    if (!room.isStarted()) {
      room.start();
    }
  }
  
  public void joinRoom(String playerName, String roomName) {
    var room = rooms.computeIfAbsent(roomName, GameRoom::new);
    room.join(retrievePlayer(playerName));
  }
  
  public void leaveRoom(String playerName, String roomName) {
    var room = rooms.get(roomName);
    if (room != null) {
      room.leave(playerName);
      if (room.isEmpty()) {
        rooms.remove(roomName);
        room.stop();
      }
    }
  }

  public void cleanupPlayer(String playerName) {
    log.info("Cleaning up player {}", playerName);
    var player = players.remove(playerName);
    if (player != null && player.getRoom() != null) {
      leaveRoom(playerName, player.getRoom().getName());
    }
  }

  private Player retrievePlayer(String playerName) {
    return players.computeIfAbsent(playerName, name -> { return
            new Player(name)
              .setModel("model")
              .setMaxHealth(500)
              .setHealth(500)
              .setAngle(0)
              .setVelocity(0)
              .setX(0)
              .setZ(0)
              .setHeight(4f)
              .setWidth(7f)
              .setLength(26f);
    });
  }
}
