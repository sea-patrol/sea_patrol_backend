package ru.sea.patrol.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import ru.sea.patrol.service.chat.ChatService;
import ru.sea.patrol.service.game.GameRoomProperties;
import ru.sea.patrol.service.game.GameService;
import ru.sea.patrol.service.game.RoomCatalogService;
import ru.sea.patrol.service.game.RoomCatalogWsService;
import ru.sea.patrol.service.game.RoomRegistry;
import ru.sea.patrol.service.game.SpawnService;
import ru.sea.patrol.service.session.GameSessionRegistry;
import ru.sea.patrol.ws.game.GameWebSocketHandler;
import ru.sea.patrol.ws.protocol.MessageType;
import ru.sea.patrol.ws.protocol.dto.MessageInput;
import tools.jackson.databind.ObjectMapper;

class WsProtocolParsingTest {

	@Test
	void parseMessage_parsesArrayTypeAndPayload() {
		GameWebSocketHandler handler = newHandler();

		MessageInput input = invokeParseMessage(handler, """
				["PLAYER_INPUT", {"left": false, "right": true, "up": true, "down": false}]
				""");

		assertThat(input.getType()).isEqualTo(MessageType.PLAYER_INPUT);
		assertThat(input.getPayload()).isNotNull();
		assertThat(input.getPayload().isObject()).isTrue();
		assertThat(input.getPayload().get("right").asBoolean()).isTrue();
	}

	@Test
	void parseMessage_parsesStringPayload() {
		GameWebSocketHandler handler = newHandler();

		MessageInput input = invokeParseMessage(handler, """
				["CHAT_JOIN", "group:party-1"]
				""");

		assertThat(input.getType()).isEqualTo(MessageType.CHAT_JOIN);
		assertThat(input.getPayload().asText()).isEqualTo("group:party-1");
	}

	@Test
	void parseMessage_rejectsEmptyMessage() {
		GameWebSocketHandler handler = newHandler();

		assertThatThrownBy(() -> invokeParseMessage(handler, "  "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Empty");
	}

	@Test
	void parseMessage_rejectsNonArrayFormat() {
		GameWebSocketHandler handler = newHandler();

		assertThatThrownBy(() -> invokeParseMessage(handler, "{\"type\":\"PLAYER_INPUT\"}"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid message format");
	}

	private static GameWebSocketHandler newHandler() {
		ObjectMapper objectMapper = new ObjectMapper();
		GameRoomProperties roomProperties = new GameRoomProperties(
				"main",
				5,
				100,
				Duration.ofMillis(100),
				Duration.ofSeconds(15)
		);
		RoomRegistry roomRegistry = new RoomRegistry(roomProperties);
		RoomCatalogService roomCatalogService = new RoomCatalogService(roomRegistry, roomProperties);
		RoomCatalogWsService roomCatalogWsService = new RoomCatalogWsService(roomCatalogService);
		ApplicationEventPublisher eventPublisher = event -> {
		};
		GameSessionRegistry sessionRegistry = new GameSessionRegistry(roomProperties, roomRegistry, roomCatalogWsService, eventPublisher);
		return new GameWebSocketHandler(
				new ChatService(objectMapper, sessionRegistry),
				new GameService(objectMapper, roomProperties, roomRegistry, sessionRegistry, new SpawnService()),
				roomCatalogWsService,
				objectMapper,
				sessionRegistry
		);
	}

	private static MessageInput invokeParseMessage(GameWebSocketHandler handler, String json) {
		try {
			Method m = GameWebSocketHandler.class.getDeclaredMethod("parseMessage", String.class);
			m.setAccessible(true);
			return (MessageInput) m.invoke(handler, json);
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof RuntimeException re) {
				throw re;
			}
			throw new RuntimeException(e.getCause());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}

