package ru.sea.patrol.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import ru.sea.patrol.MessageType;
import ru.sea.patrol.dto.chat.ChatMessage;
import ru.sea.patrol.dto.chat.GameMessageInput;
import ru.sea.patrol.service.MessageService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatService implements MessageService {

  private static final String GLOBAL_CHAT_GROUP = "global";

  private final ObjectMapper objectMapper = new ObjectMapper();

  // Groups: "global", "group:123", "user:alice"
  private final Map<String, Sinks.Many<ChatMessage>> groupSinks = new ConcurrentHashMap<>();

  private final Map<String, ChatUser> users = new ConcurrentHashMap<>();

  @Override
  public Flux<WebSocketMessage> initialize(String username, WebSocketSession session) {
    var user = addUser(username);
    userJoinChatMessage(user);
    addUserToBaseGroup(user);
    log.info("Player {} joined chat", username);
    return user.getUserSink().asFlux()
            .map(msg -> createWebSocketMessage(MessageType.CHAT_MESSAGE.name(), msg, session, objectMapper))
            .doOnCancel(() -> cleanupUser(username));
  }

  public void joinToGroup(String username, String groupName) {
    var user = users.get(username);
    if (user != null) {
      addToGroup(groupName, user);
    }
  }

  public void leaveFromGroup(String username, String groupName) {
    var user = users.get(username);
    if (user != null) {
      user.removeSubscription(groupName);
    }
  }

  public Mono<Void> handle(String username, GameMessageInput msg) {
    switch (msg.getType()) {
      case MessageType.CHAT_MESSAGE:
        return handleChatMessage(username, msg.getPayload());
      case MessageType.CHAT_JOIN:
        String groupId = msg.getPayload().asText();
        joinToGroup(username, groupId);
        return Mono.empty();
      case MessageType.CHAT_LEAVE:
        String gid = msg.getPayload().asText();
        leaveFromGroup(username, gid);
        return Mono.empty();
      default:
        return Mono.empty();
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

      if (GLOBAL_CHAT_GROUP.equals(to)) {
        broadcast(GLOBAL_CHAT_GROUP, msg);
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

  private ChatUser addUser(String userName) {
      return users.computeIfAbsent(userName, k -> new ChatUser(userName));
  }

  private void addUserToBaseGroup(ChatUser user) {
    addToGroup( GLOBAL_CHAT_GROUP, user);
    addToGroup( "user:" + user.getUserName(), user);
  }

  private void addToGroup(String groupName, ChatUser user) {
    var groupSync = getOrCreateGroupSink(groupName);
    user.addSubscription(groupName, groupSync);
  }

  private Sinks.Many<ChatMessage> getOrCreateGroupSink(String topic) {
    return groupSinks.computeIfAbsent(topic,
            k -> Sinks.many().replay().limit(10));
  }

  private void cleanupUser(String username) {
    var user = users.remove(username);
    if (user != null) {
      user.cleanupSubscriptions();
      userLeaveChatMessage(user);
      log.info("Player {} left chat", username);
    }
  }

  private void broadcast(String group, ChatMessage msg) {
    Sinks.Many<ChatMessage> sink = getOrCreateGroupSink(group);
    sink.tryEmitNext(msg);
  }

  private void userJoinChatMessage(ChatUser user) {
    if (user != null) {
      broadcast(GLOBAL_CHAT_GROUP, new ChatMessage("system", GLOBAL_CHAT_GROUP, user.getUserName() + " joined the chat"));
    }
  }

  private void userLeaveChatMessage(ChatUser user) {
    if (user != null) {
      broadcast(GLOBAL_CHAT_GROUP, new ChatMessage("system", GLOBAL_CHAT_GROUP, user.getUserName() + " left the chat"));
    }
  }
}
