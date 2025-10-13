package ru.sea.patrol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;
import ru.sea.patrol.dto.game.PlayerInput;
import ru.sea.patrol.dto.game.PlayerLeft;
import ru.sea.patrol.dto.game.PlayerState;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

  private final Map<String, PlayerState> playerStates = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper = new ObjectMapper();

  // Единый sink для рассылки обновлений ВСЕМ игрокам
  private final Sinks.Many<PlayerState> globalPlayerStateSink =
      Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

  // Новый sink для событий "игрок вышел"
  private final Sinks.Many<PlayerLeft> playerLeftSink =
      Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

  // Обновление позиции игрока
  public Mono<Void> handlePlayerInput(String username, JsonNode payload) {
    try {
      var input = objectMapper.treeToValue(payload, PlayerInput.class);
      var state = playerStates.computeIfAbsent(username, k -> new PlayerState(username, generateRandomColor(), 100, 100));

      if (input.isPressed()) {
        // Обновляем позицию
        if ("ArrowUp".equals(input.getKey()))
          state.setY(Math.max(0, state.getY() - 5));
        if ("ArrowDown".equals(input.getKey()))
          state.setY(state.getY() + 5);
        if ("ArrowLeft".equals(input.getKey()))
          state.setX(Math.max(0, state.getX() - 5));
        if ("ArrowRight".equals(input.getKey()))
          state.setX(state.getX() + 5);

        // Рассылаем обновление ВСЕМ
        globalPlayerStateSink.tryEmitNext(state);
      }

      return Mono.empty();
    } catch (Exception e) {
      return Mono.error(e);
    }
  }

  public Collection<PlayerState> initAndReturnAllPlayers(String username) {
    var state = playerStates.computeIfAbsent(username, k -> new PlayerState(username, generateRandomColor(), 100, 100));
    globalPlayerStateSink.tryEmitNext(state);
    return playerStates.values();
  }

  // Получить поток обновлений позиций
  public Flux<PlayerState> getStateUpdates() {
    return globalPlayerStateSink.asFlux();
  }

  public static String generateRandomColor() {
    return "#" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
  }

  public void removePlayer(String username) {
    // Удаляем из состояния
    playerStates.remove(username);

    // Рассылаем событие "игрок вышел"
    playerLeftSink.tryEmitNext(new PlayerLeft(username));
  }

  public Flux<PlayerLeft> getPlayerLeftEvents() {
    return playerLeftSink.asFlux();
  }
}