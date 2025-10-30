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

  public Flux<MessageOutput> initialize(String playerName) {
    var player = retrievePlayer(playerName);
    return player.getSink().asFlux();
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
    var user = players.remove(playerName);
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
              .setHeight(1.5f)
              .setWidth(2f)
              .setLength(8f);
    });
  }
}
