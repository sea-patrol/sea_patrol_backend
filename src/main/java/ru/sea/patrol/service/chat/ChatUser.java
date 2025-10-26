package ru.sea.patrol.service.chat;

import lombok.Getter;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import ru.sea.patrol.dto.chat.ChatMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class ChatUser {

    private final String userName;

    private final Sinks.Many<ChatMessage> userSink = Sinks.many().unicast().onBackpressureBuffer();

    // topic -> subscription
    private final Map<String, Disposable> userSubscriptions = new ConcurrentHashMap<>();

    public ChatUser(String userName) {
        this.userName = userName;
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

    public void cleanupSubscriptions() {
        userSubscriptions.values().forEach(Disposable::dispose);
        userSubscriptions.clear();
    }
}
