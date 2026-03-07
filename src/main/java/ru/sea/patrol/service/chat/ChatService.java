package ru.sea.patrol.service.chat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.sea.patrol.ws.protocol.MessageType;
import ru.sea.patrol.ws.protocol.dto.ChatMessage;
import ru.sea.patrol.ws.protocol.dto.MessageInput;
import ru.sea.patrol.ws.protocol.dto.MessageOutput;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

	private static final String GLOBAL_CHAT_GROUP = "global";
	private static final String LOBBY_CHAT_GROUP = "group:lobby";
	private static final String ROOM_CHAT_PREFIX = "group:room:";

	private final ObjectMapper objectMapper;
	private final Map<String, ChatGroup> groups = new ConcurrentHashMap<>();
	private final Map<String, ChatUser> users = new ConcurrentHashMap<>();

	public Flux<MessageOutput> initialize(String username) {
		var user = addUser(username);
		userJoinChatMessage(user);
		addUserToBaseGroups(user);
		log.info("Player {} joined chat", username);
		return user.getUserSink().asFlux()
				.map(msg -> new MessageOutput(MessageType.CHAT_MESSAGE, msg));
	}

	public void cleanupUser(String username) {
		var user = users.remove(username);
		if (user != null) {
			var userGroupNames = user.cleanupSubscriptions();
			for (String groupName : userGroupNames) {
				var group = groups.get(groupName);
				if (group != null) {
					group.left(username);
					removeGroupIfEmpty(group);
				}
			}
			userLeaveChatMessage(user);
			log.info("Player {} left chat", username);
		}
	}

	public void joinToGroup(String username, String groupName) {
		var user = users.get(username);
		if (user != null) {
			addToGroup(groupName, user);
		}
	}

	public void leaveFromGroup(String username, String groupName) {
		var group = groups.get(groupName);
		if (group != null) {
			group.left(username);
			removeGroupIfEmpty(group);
		}
	}

	public void moveUserToRoom(String username, String roomId) {
		leaveFromGroup(username, LOBBY_CHAT_GROUP);
		joinToGroup(username, ROOM_CHAT_PREFIX + roomId);
	}

	public Mono<Void> handle(String username, MessageInput msg) {
		switch (msg.getType()) {
			case MessageType.CHAT_MESSAGE:
				return handleChatMessage(username, msg.getPayload());
			case MessageType.CHAT_JOIN:
				joinToGroup(username, msg.getPayload().asText());
				return Mono.empty();
			case MessageType.CHAT_LEAVE:
				leaveFromGroup(username, msg.getPayload().asText());
				return Mono.empty();
			default:
				return Mono.empty();
		}
	}

	public Mono<Void> handleChatMessage(String fromUserId, JsonNode payload) {
		try {
			ChatMessage msg = objectMapper.treeToValue(payload, ChatMessage.class);
			msg.setFrom(fromUserId);
			log.info("Got message: {}", msg);
			String to = msg.getTo();
			if (to == null) {
				return Mono.error(new IllegalArgumentException("Missing 'to'"));
			}

			if (GLOBAL_CHAT_GROUP.equals(to)) {
				broadcast(GLOBAL_CHAT_GROUP, msg);
			} else if (to.startsWith("group:")) {
				broadcast(to, msg);
			} else if (to.startsWith("user:")) {
				broadcast(to, msg);
				broadcast("user:" + fromUserId, msg);
			} else {
				return Mono.error(new IllegalArgumentException("Invalid 'to' value"));
			}
			return Mono.empty();
		} catch (Exception e) {
			return Mono.error(e);
		}
	}

	private ChatUser addUser(String userName) {
		return users.computeIfAbsent(userName, ChatUser::new);
	}

	private void addUserToBaseGroups(ChatUser user) {
		addToGroup(GLOBAL_CHAT_GROUP, user);
		addToGroup("user:" + user.getName(), user);
		addToGroup(LOBBY_CHAT_GROUP, user);
	}

	private void addToGroup(String groupName, ChatUser user) {
		var group = getOrCreateGroup(groupName);
		group.join(user);
	}

	private ChatGroup getOrCreateGroup(String groupName) {
		return groups.computeIfAbsent(groupName, ChatGroup::new);
	}

	private void broadcast(String groupName, ChatMessage msg) {
		var group = getOrCreateGroup(groupName);
		group.send(msg);
	}

	private void userJoinChatMessage(ChatUser user) {
		if (user != null) {
			broadcast(GLOBAL_CHAT_GROUP, new ChatMessage("system", GLOBAL_CHAT_GROUP, user.getName() + " joined the chat"));
		}
	}

	private void userLeaveChatMessage(ChatUser user) {
		if (user != null) {
			broadcast(GLOBAL_CHAT_GROUP, new ChatMessage("system", GLOBAL_CHAT_GROUP, user.getName() + " left the chat"));
		}
	}

	private void removeGroupIfEmpty(ChatGroup group) {
		if (group != null && group.isEmpty()) {
			groups.remove(group.getName());
		}
	}
}
