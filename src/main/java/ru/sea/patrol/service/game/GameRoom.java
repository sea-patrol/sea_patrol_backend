package ru.sea.patrol.service.game;

import lombok.Getter;
import reactor.core.publisher.Sinks;
import ru.sea.patrol.dto.websocket.MessageOutput;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameRoom {

  @Getter
  private final String name;

  private final Map<String, Player> players = new ConcurrentHashMap<>();

  @Getter
  private final Sinks.Many<MessageOutput> sink;

  public GameRoom(String name) {
    this.name = name;
    this.sink = Sinks.many().unicast().onBackpressureBuffer();
  }

  public void join(Player player) {
    players.put(player.getName(), player);
    player.joinRoom(this);
  }

  public void leave(String playerName) {
    var player = players.remove(playerName);
    if (player != null) {
      player.leaveRoom();
    }
  }

  public boolean isEmpty() {
    return players.isEmpty();
  }

  public void start() {

  }

  public void stop() {

  }
}
