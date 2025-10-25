package ru.sea.patrol.service.chat;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class ChatService {

  private final ObjectMapper objectMapper = new ObjectMapper();

  // Топики: "global", "group:123", "user:alice"
  private final Map<String, Sinks.Many<ChatMessage>> topicSinks = new ConcurrentHashMap<>();

  private final Map<String, ChatUser> users = new ConcurrentHashMap<>();

  public void addUser(String userName) {
    users.putIfAbsent(userName, new ChatUser(userName));
  }

  public Flux<ChatMessage> getMessagesForUser(String userName) {
    ChatUser user = users.computeIfAbsent(userName, k -> new ChatUser(userName));

    subscribeUserToBaseTopics(userName);

    return user.getUserSink().asFlux().doOnCancel(() -> cleanupUser(userName));
  }

  private void subscribeUserToBaseTopics(String username) {
    var user = users.get(username);
    if (user == null) return;

    user.cleanupSubscriptions();

    List<Disposable> subs = new CopyOnWriteArrayList<>();
    addSubscription(subs, "global", username);
    addSubscription(subs, "user:" + username, username);

    user.getUserGroups()
        .forEach(groupId -> addSubscription(subs, "group:" + groupId, username));

    user.getUserSubscriptions().addAll(subs);
  }

  private void addSubscription(List<Disposable> list, String topic, String username) {
    Disposable sub = getOrCreateTopicSink(topic)
        .asFlux()
        .subscribe(msg -> relayToUser(username, msg));
    list.add(sub);
  }

  private Sinks.Many<ChatMessage> getOrCreateTopicSink(String topic) {
    return topicSinks.computeIfAbsent(topic,
            k -> Sinks.many().replay().limit(10));
  }

  private void relayToUser(String username, ChatMessage msg) {
    Sinks.Many<ChatMessage> sink = users.get(username).getUserSink();
    if (sink != null) {
      sink.tryEmitNext(msg);
    }
  }

  private void cleanupUser(String username) {
    var user = users.remove(username);
    if (user != null) {
      user.destroy();
    }
  }

  public void addUserToGroup(String username, String groupId) {
    var user = users.get(username);
    if (user != null) {
      user.getUserGroups().add(groupId);
    }
    subscribeUserToBaseTopics(username);
  }

  public void removeUserFromGroup(String username, String groupId) {
    var user = users.get(username);
    if (user != null) {
      var groups = user.getUserGroups();
      if (groups != null)
        groups.remove(groupId);
      subscribeUserToBaseTopics(username);
    }
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
    Sinks.Many<ChatMessage> sink = getOrCreateTopicSink(topic);
    sink.tryEmitNext(msg);
  }
}
