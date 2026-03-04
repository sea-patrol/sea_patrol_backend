package ru.sea.patrol.service.game;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;
import ru.sea.patrol.MessageType;
import ru.sea.patrol.dto.websocket.*;

import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public class GameRoom {

  private ObjectMapper objectMapper = new ObjectMapper();

  @Getter
  private final String name;

  private final Map<String, Player> players = new ConcurrentHashMap<>();

  @Getter
  private final Sinks.Many<MessageOutput> sink;

  @Getter
  private World world;
  private Wind wind;

  private long lastTime = System.nanoTime();
  private float delta;

  private ScheduledExecutorService scheduler;
  private ScheduledFuture<?> scheduledFuture;

  @Getter
  private volatile boolean started = false;

  private long updatePeriod = 100L;

  public GameRoom(String name) {
    this.name = name;
    this.sink = Sinks.many().multicast().onBackpressureBuffer();
  }

  public void join(Player player) {
    log.info("Player {} joining room {}", player.getName(), name);
    players.put(player.getName(), player);
    player.joinRoom(this);
    if (started) {
      broadcastPlayerJoinMessage(player);
      sendStartMessage(player);
    }
  }

  public void leave(String playerName) {
    log.info("Player {} leaving room {}", playerName, name);
    var player = players.remove(playerName);
    if (player != null) {
      player.leaveRoom();
      if (started) {
        broadcastPlayerLeaveMessage(player);
      }
    }
  }

  public boolean isEmpty() {
    return players.isEmpty();
  }

  public void start() {
    start(true);
  }

  public void start(boolean scheduleUpdates) {
    log.info("Starting room game {}", name);
    if (started) {
      return;
    }
    world = new World(new Vector2(0, 0), true);
    wind = new Wind();
    for (var player : players.values()) {
      player.createShipInstanceInGameWorld(world);
    }

    lastTime = System.nanoTime();
    delta = retrieveDelta();

    started = true;

    broadcastStartMessage();

    if (scheduleUpdates) {
      scheduler = Executors.newSingleThreadScheduledExecutor();
      scheduledFuture = scheduler.scheduleWithFixedDelay(
              this::update, 0, updatePeriod, TimeUnit.MILLISECONDS);
    }
  }

  public void update() {
    delta = retrieveDelta();

    wind.update(delta);

    // Обновляем игроков
    players.values().forEach(player -> {
      if (player.getShip() != null) {
        player.getShip().update(delta, wind);
      }
    });

    // Шаг физики
    world.step(delta, 6, 2);

    sendUpdateMessage();
  }

  public void stop() {
    log.info("Stopping room game {}", name);
    if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
      scheduledFuture.cancel(false);
    }
    if (scheduler != null) {
      scheduler.shutdownNow();
      scheduler = null;
    }
    for (var player : players.values()) {
      player.leaveRoom();
    }
    if (world != null) {
      world.dispose();
      world = null;
    }
    wind = null;
    started = false;
  }

  private float retrieveDelta() {
    long currentTime = System.nanoTime();
    float delta = (currentTime - lastTime) / 1_000_000_000.0f;
    lastTime = currentTime;
    return delta;
  }

  private void broadcastStartMessage() {
    var startMessage = new MessageOutput(MessageType.INIT_GAME_STATE, new InitGameStateMessage(
            name,
            new WindInfo(wind.getDirection().angleRad(), wind.getSpeed()),
            players.values().stream().map(player -> new PlayerInfo(
                    player.getName(),
                    player.getHealth(),
                    player.getMaxHealth(),
                    player.getShip().getVelocity(),
                    player.getShip().getFrontendX(),  //inverted axis for client
                    player.getShip().getFrontendZ(),  //inverted axis for client
                    player.getShip().getOrientation(),
                    player.getModel(),
                    player.getHeight(),
                    player.getWidth(),
                    player.getLength())
            ).collect(Collectors.toList())
    ));

    sink.tryEmitNext(startMessage);
    log.info("Room game {} sent start message", name);
  }

  private void sendStartMessage(Player player) {
    var startMessage = new MessageOutput(MessageType.INIT_GAME_STATE, new InitGameStateMessage(
            name,
            new WindInfo(wind.getDirection().angleRad(), wind.getSpeed()),
            players.values().stream().map(p -> new PlayerInfo(
                    p.getName(),
                    p.getHealth(),
                    p.getMaxHealth(),
                    p.getShip().getVelocity(),
                    p.getShip().getFrontendX(), //inverted axis for client
                    p.getShip().getFrontendZ(),  //inverted axis for client
                    p.getShip().getOrientation(),
                    p.getModel(),
                    p.getHeight(),
                    p.getWidth(),
                    p.getLength())
            ).collect(Collectors.toList())
    ));

    player.getSink().tryEmitNext(startMessage);
    log.info("Room game {} sent start message for player {}", name, player.getName());
  }

  private void sendUpdateMessage() {
    var updateMessage = new MessageOutput(MessageType.UPDATE_GAME_STATE, new UpdateGameStateMessage(
            delta,
            new WindInfo(wind.getDirection().angleRad(), wind.getSpeed()),
            players.values().stream().map(player -> new PlayerUpdateInfo(
                    player.getName(),
                    player.getHealth(),
                    player.getShip().getVelocity(),
                    player.getShip().getFrontendX(),  //inverted axis for client
                    player.getShip().getFrontendZ(),  //inverted axis for client
                    player.getShip().getOrientation())
            ).collect(Collectors.toList())
    ));

    sink.tryEmitNext(updateMessage);
  }

  private void broadcastPlayerJoinMessage(Player player) {
    var joinMessage = new MessageOutput(MessageType.PLAYER_JOIN, new PlayerInfo(
            player.getName(),
            player.getHealth(),
            player.getMaxHealth(),
            player.getShip().getVelocity(),
            player.getShip().getFrontendX(),
            player.getShip().getFrontendZ(),
            player.getShip().getOrientation(),
            player.getModel(),
            player.getHeight(),
            player.getWidth(),
            player.getLength()));
    sink.tryEmitNext(joinMessage);
    log.info("Player {} joined in the room game {}", player.getName(), name);
  }

  private void broadcastPlayerLeaveMessage(Player player) {
    var leaveMessage = new MessageOutput(MessageType.PLAYER_LEAVE, player.getName());
    sink.tryEmitNext(leaveMessage);
    log.info("Player {} left the room game {}", player.getName(), name);
  }
}
