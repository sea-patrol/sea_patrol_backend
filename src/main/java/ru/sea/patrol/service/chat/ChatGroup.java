package ru.sea.patrol.service.chat;

import lombok.Getter;
import reactor.core.publisher.Sinks;
import ru.sea.patrol.dto.chat.ChatMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatGroup {

  @Getter
  private final String name;

  private final Sinks.Many<ChatMessage> sink;

  private Map<String, ChatUser> users = new ConcurrentHashMap<>();

  public ChatGroup(String name) {
    this.name = name;
    this.sink = Sinks.many().replay().limit(10);
  }

  public boolean isEmpty() {
    return users.isEmpty();
  }

  public void send(ChatMessage message) {
    sink.tryEmitNext(message);
  }

  public void join(ChatUser user) {
    user.addSubscription(name, sink);
    users.putIfAbsent(user.getName(), user);
  }

  public void left(String username) {
    var user = users.remove(username);
    if (user != null) {
      user.removeSubscription(name);
    }
  }
}
