package ru.sea.patrol.ws.game;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.sea.patrol.service.chat.ChatService;
import ru.sea.patrol.service.game.GameService;
import ru.sea.patrol.service.game.RoomCatalogWsService;
import ru.sea.patrol.service.session.GameSessionRegistry;
import ru.sea.patrol.ws.protocol.MessageType;
import ru.sea.patrol.ws.protocol.dto.MessageInput;
import ru.sea.patrol.ws.protocol.dto.MessageOutput;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler implements WebSocketHandler {

	private final ChatService chatService;
	private final GameService gameService;
	private final RoomCatalogWsService roomCatalogWsService;
	private final ObjectMapper objectMapper;
	private final GameSessionRegistry sessionRegistry;

	@Override
	public Mono<Void> handle(WebSocketSession session) {
		return ReactiveSecurityContextHolder.getContext()
				.map(securityContext -> securityContext.getAuthentication().getName())
				.flatMap(username -> {
					String sessionId = session.getId();
					var claimResult = sessionRegistry.claimSession(username, sessionId);
					if (claimResult == GameSessionRegistry.ClaimResult.REJECTED_DUPLICATE) {
						log.warn("Rejected duplicate WebSocket session for user {}", username);
						return session.close(CloseStatus.POLICY_VIOLATION.withReason(GameSessionRegistry.DUPLICATE_SESSION_ERROR_CODE));
					}

					Flux<WebSocketMessage> chatFlux = chatService.initialize(username)
							.map(message -> createWebSocketMessage(message, session));
					Flux<WebSocketMessage> gameFlux = gameService.initialize(username)
							.map(message -> createWebSocketMessage(message, session));
					Flux<WebSocketMessage> roomCatalogFlux = Flux.concat(
							Mono.defer(() -> sessionRegistry.hasActiveLobbySession(username)
									? Mono.just(roomCatalogWsService.snapshotMessage())
									: Mono.empty()),
							roomCatalogWsService.initialize()
									.filter(__ -> sessionRegistry.hasActiveLobbySession(username))
					).map(message -> createWebSocketMessage(message, session));
					Flux<WebSocketMessage> outbound = Flux.merge(chatFlux, gameFlux, roomCatalogFlux);

					Flux<MessageInput> inbound = session.receive()
							.map(WebSocketMessage::getPayloadAsText)
							.flatMap(payload -> Mono.fromCallable(() -> parseMessage(payload))
									.onErrorResume(ex -> {
										log.warn("Invalid inbound WS message for user {}: {}", username, ex.getMessage());
										return Mono.empty();
									}));

					Mono<Void> input = inbound
							.flatMap(msg -> handleInbound(username, msg)
									.onErrorResume(ex -> {
										log.error("Error processing WS message for user {}: {}", username, ex.getMessage(), ex);
										return Mono.empty();
									}))
							.then();

					AtomicBoolean cleanedUp = new AtomicBoolean(false);
					Mono<Void> sessionFlow = session.send(outbound)
							.and(input)
							.and(session.closeStatus().then())
							.doFinally(signalType -> cleanupSession(username, sessionId, cleanedUp));

					return sessionFlow;
				})
				.then();
	}

	private void cleanupSession(String username, String sessionId, AtomicBoolean cleanedUp) {
		if (!cleanedUp.compareAndSet(false, true)) {
			return;
		}
		if (!sessionRegistry.registerDisconnect(username, sessionId)) {
			log.debug("Skipped stale disconnect cleanup for user {} and session {}", username, sessionId);
			return;
		}
		chatService.cleanupUser(username);
		boolean roomCatalogChanged = gameService.cleanupPlayer(username);
		if (roomCatalogChanged) {
			roomCatalogWsService.publishRoomsUpdated();
		}
		log.info("Player {} disconnected", username);
	}

	private Mono<Void> handleInbound(String username, MessageInput msg) {
		return switch (msg.getType()) {
			case MessageType.CHAT_MESSAGE, MessageType.CHAT_JOIN, MessageType.CHAT_LEAVE -> chatService.handle(username, msg);
			case MessageType.PLAYER_INPUT -> gameService.handle(username, msg);
			default -> Mono.empty();
		};
	}

	private MessageInput parseMessage(String json) {
		if (json == null || json.trim().isEmpty()) {
			throw new IllegalArgumentException("Empty message");
		}
		try {
			JsonNode node = objectMapper.readTree(json);
			if (!node.isArray() || node.size() < 2) {
				throw new IllegalArgumentException("Expected [type, payload]");
			}
			MessageInput msg = new MessageInput();
			msg.setType(MessageType.valueOf(node.get(0).asText()));
			msg.setPayload(node.get(1));
			return msg;
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid message format", e);
		}
	}

	private WebSocketMessage createWebSocketMessage(MessageOutput messageOutput, WebSocketSession session) {
		try {
			ObjectNode node = objectMapper.createObjectNode();
			node.put("type", messageOutput.getType().toString());
			node.set("payload", objectMapper.valueToTree(messageOutput.getPayload()));
			return session.textMessage(node.toString());
		} catch (Exception e) {
			throw new RuntimeException("Serialization error", e);
		}
	}
}

