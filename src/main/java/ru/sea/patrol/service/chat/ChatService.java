package ru.sea.patrol.service.chat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.sea.patrol.service.session.GameSessionRegistry;
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

	private static final String USER_CHAT_PREFIX = "user:";
	private static final String LOBBY_CHAT_GROUP = "group:lobby";
	private static final String ROOM_CHAT_PREFIX = "group:room:";

	private final ObjectMapper objectMapper;
	private final GameSessionRegistry sessionRegistry;
	private final Map<String, ChatGroup> groups = new ConcurrentHashMap<>();
	private final Map<String, ChatUser> users = new ConcurrentHashMap<>();

	public Flux<MessageOutput> initialize(String username) {
		var user = addUser(username);
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

	public void moveUserToLobby(String username, String roomId) {
		if (roomId != null && !roomId.isBlank()) {
			leaveFromGroup(username, ROOM_CHAT_PREFIX + roomId);
		}
		joinToGroup(username, LOBBY_CHAT_GROUP);
	}

	public Mono<Void> handle(String username, MessageInput msg) {
		switch (msg.getType()) {
			case MessageType.CHAT_MESSAGE:
				return handleChatMessage(username, msg.getPayload());
			case MessageType.CHAT_JOIN, MessageType.CHAT_LEAVE:
				log.debug("Ignoring client-managed chat membership change for user {} and type {}", username, msg.getType());
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

			if (to.startsWith(USER_CHAT_PREFIX)) {
				broadcast(to, msg);
				broadcast(USER_CHAT_PREFIX + fromUserId, msg);
			} else {
				String scopedGroup = resolvePublicChatScope(fromUserId);
				msg.setTo(scopedGroup);
				broadcast(scopedGroup, msg);
			}
			return Mono.empty();
		} catch (Exception e) {
			return Mono.error(e);
		}
	}

	private String resolvePublicChatScope(String username) {
		String roomId = sessionRegistry.activeRoomId(username);
		if (roomId != null && !roomId.isBlank()) {
			return ROOM_CHAT_PREFIX + roomId;
		}
		if (sessionRegistry.hasActiveLobbySession(username)) {
			return LOBBY_CHAT_GROUP;
		}
		throw new IllegalArgumentException("No active public chat scope for user " + username);
	}

	private ChatUser addUser(String userName) {
		return users.computeIfAbsent(userName, ChatUser::new);
	}

	private void addUserToBaseGroups(ChatUser user) {
		addToGroup(USER_CHAT_PREFIX + user.getName(), user);
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

	private void removeGroupIfEmpty(ChatGroup group) {
		if (group != null && group.isEmpty()) {
			groups.remove(group.getName());
		}
	}
}
