package ru.sea.patrol.service.chat;

import lombok.Getter;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import ru.sea.patrol.ws.protocol.dto.ChatMessage;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class ChatUser {

    private final String name;

    private final Sinks.Many<ChatMessage> userSink = Sinks.many().unicast().onBackpressureBuffer();

    // groupName -> subscription
    private final Map<String, Disposable> userSubscriptions = new ConcurrentHashMap<>();

    public ChatUser(String name) {
        this.name = name;
    }

    public void reply(ChatMessage message) {
        if (message != null) {
            userSink.tryEmitNext(message);
        }
    }

    public void addSubscription(String groupName, Sinks.Many<ChatMessage> groupSink) {
        Disposable subscription = groupSink.asFlux()
                .subscribe(this::reply);
        userSubscriptions.put(groupName, subscription);
    }

    public void removeSubscription(String topic) {
        if (userSubscriptions.containsKey(topic)) {
            userSubscriptions.remove(topic).dispose();
        }
    }

    public Set<String> cleanupSubscriptions() {
        var groupNames = new HashSet<>(userSubscriptions.keySet());
        userSubscriptions.values().forEach(Disposable::dispose);
        userSubscriptions.clear();
        return groupNames;
    }
}
