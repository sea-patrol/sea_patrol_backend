package ru.sea.patrol.service.game;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;
import ru.sea.patrol.MessageType;
import ru.sea.patrol.dto.websocket.InitGameStateMessage;
import ru.sea.patrol.dto.websocket.MessageOutput;
import ru.sea.patrol.dto.websocket.PlayerInfo;
import ru.sea.patrol.dto.websocket.WindInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class GameRoom {

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

  @Getter
  private volatile boolean started = false;

  private long updatePeriod = 1000L;

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

    scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.scheduleAtFixedRate(this::update, 0, updatePeriod, TimeUnit.MILLISECONDS);

    sendStartMessage();
  }

  public void update() {
    delta = retrieveDelta();

    wind.update(delta);

    // Обновляем игроков
    players.values().forEach(player -> player.getShip().update(delta, wind));

    // Шаг физики
    world.step(delta, 6, 2);

    log.info("Delta: {}", delta);
  }

  public void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
      scheduler = null;
    }
    if (world != null) {
      world.dispose();
      world = null;
    }
    wind = null;
    for (var player : players.values()) {
      player.leaveRoom();
    }
    started = false;
  }

  private float retrieveDelta() {
    long currentTime = System.nanoTime();
    float delta = (currentTime - lastTime) / 1_000_000_000.0f;
    lastTime = currentTime;
    return delta;
  }

  private void sendStartMessage() {
    var startMessage = new MessageOutput(MessageType.INIT_GAME_STATE, new InitGameStateMessage(
            new WindInfo(wind.getDirection().angleRad(), wind.getSpeed()),
            players.values().stream().map(player -> new PlayerInfo(
                    player.getName(),
                    player.getHealth(),
                    player.getMaxHealth(),
                    player.getShip().getVelocity(),
                    player.getShip().getPosition().x,
                    player.getShip().getPosition().y,
                    player.getShip().getOrientation(),
                    player.getModel(),
                    player.getHeight(),
                    player.getWidth(),
                    player.getLength())
            ).collect(Collectors.toList())
    ));

    sink.tryEmitNext(startMessage);
  }

  private void sendUpdateMessage() {

  }
}
