package ru.sea.patrol.service.chat;

import lombok.Getter;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import ru.sea.patrol.dto.chat.ChatMessage;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class ChatUser {

    // userId -> Set<groupId>
    private final Set<String> userGroups = new ConcurrentSkipListSet<>();

    // userId -> активные подписки
    private final List<Disposable> userSubscriptions = new CopyOnWriteArrayList<>();

    // userId -> агрегирующий sink
    private final Sinks.Many<ChatMessage> userSink = Sinks.many().unicast().onBackpressureBuffer();

    private final String userName;

    public ChatUser(String userName) {
        this.userName = userName;
    }

    public void destroy() {
        cleanupSubscriptions();
    }

    public void cleanupSubscriptions() {
        userSubscriptions.forEach(Disposable::dispose);
        userSubscriptions.clear();
    }
}
