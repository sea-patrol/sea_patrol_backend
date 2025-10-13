package ru.sea.patrol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import ru.sea.patrol.dto.chat.ChatMessage;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class ChatService {

  private final ObjectMapper objectMapper = new ObjectMapper();

  // Топики: "global", "group:123", "user:alice"
  private final Map<String, Sinks.Many<ChatMessage>> topicSinks = new ConcurrentHashMap<>();

  // userId -> Set<groupId>
  private final Map<String, Set<String>> userGroups = new ConcurrentHashMap<>();

  // userId -> активные подписки
  private final Map<String, List<Disposable>> userSubscriptions = new ConcurrentHashMap<>();

  // userId -> агрегирующий sink
  private final Map<String, Sinks.Many<ChatMessage>> userSinks = new ConcurrentHashMap<>();

  public Flux<ChatMessage> getMessagesForUser(String userName) {
    Sinks.Many<ChatMessage> userSink = userSinks.computeIfAbsent(userName,
        k -> Sinks.many().unicast().onBackpressureBuffer());

    subscribeUserToBaseTopics(userName);

    return userSink.asFlux().doOnCancel(() -> cleanupUser(userName));
  }

  private void subscribeUserToBaseTopics(String username) {
    cleanupSubscriptions(username);

    List<Disposable> subs = new CopyOnWriteArrayList<>();
    addSubscription(subs, "global", username);
    addSubscription(subs, "user:" + username, username);

    userGroups.getOrDefault(username, Set.of())
        .forEach(groupId -> addSubscription(subs, "group:" + groupId, username));

    userSubscriptions.put(username, subs);
  }

  private void addSubscription(List<Disposable> list, String topic, String username) {
    Disposable sub = getOrCreateSink(topic)
        .asFlux()
        .subscribe(msg -> relayToUser(username, msg));
    list.add(sub);
  }

  private void relayToUser(String username, ChatMessage msg) {
    Sinks.Many<ChatMessage> sink = userSinks.get(username);
    if (sink != null) {
      sink.tryEmitNext(msg);
    }
  }

  private Sinks.Many<ChatMessage> getOrCreateSink(String topic) {
    return topicSinks.computeIfAbsent(topic,
        k -> Sinks.many().replay().limit(10));
  }

  private void cleanupSubscriptions(String userId) {
    List<Disposable> subs = userSubscriptions.remove(userId);
    if (subs != null)
      subs.forEach(Disposable::dispose);
  }

  private void cleanupUser(String userId) {
    cleanupSubscriptions(userId);
    userSinks.remove(userId);
  }

  public void addUserToGroup(String userId, String groupId) {
    userGroups.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(groupId);
    subscribeUserToBaseTopics(userId);
  }

  public void removeUserFromGroup(String userId, String groupId) {
    Set<String> groups = userGroups.get(userId);
    if (groups != null)
      groups.remove(groupId);
    subscribeUserToBaseTopics(userId);
  }

  public Mono<Void> handleChatMessage(String fromUserId, JsonNode payload) {
    try {
      ChatMessage msg = objectMapper.treeToValue(payload, ChatMessage.class);
      msg.setFrom(fromUserId);
      log.info("Got message: " + msg);
      String to = msg.getTo();
      if (to == null)
        return Mono.error(new IllegalArgumentException("Missing 'to'"));

      if ("global".equals(to)) {
        broadcast("global", msg);
      } else if (to.startsWith("group:")) {
        broadcast(to, msg);
      } else if (to.startsWith("user:")) {
        broadcast(to, msg);
        broadcast("user:" + fromUserId, msg); // копия отправителю
      } else {
        return Mono.error(new IllegalArgumentException("Invalid 'to' value"));
      }
      return Mono.empty();
    } catch (Exception e) {
      return Mono.error(e);
    }
  }

  private void broadcast(String topic, ChatMessage msg) {
    Sinks.Many<ChatMessage> sink = getOrCreateSink(topic);
    sink.tryEmitNext(msg);
  }
}
