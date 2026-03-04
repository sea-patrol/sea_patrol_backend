package ru.sea.patrol.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import ru.sea.patrol.MessageType;
import ru.sea.patrol.dto.websocket.MessageInput;
import ru.sea.patrol.handler.GameWebSocketHandler;
import ru.sea.patrol.service.chat.ChatService;
import ru.sea.patrol.service.game.GameService;

class WsProtocolParsingTest {

	@Test
	void parseMessage_parsesArrayTypeAndPayload() {
		GameWebSocketHandler handler = new GameWebSocketHandler(new ChatService(), new GameService());

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
		GameWebSocketHandler handler = new GameWebSocketHandler(new ChatService(), new GameService());

		MessageInput input = invokeParseMessage(handler, """
				["CHAT_JOIN", "group:party-1"]
				""");

		assertThat(input.getType()).isEqualTo(MessageType.CHAT_JOIN);
		assertThat(input.getPayload().asText()).isEqualTo("group:party-1");
	}

	@Test
	void parseMessage_rejectsEmptyMessage() {
		GameWebSocketHandler handler = new GameWebSocketHandler(new ChatService(), new GameService());

		assertThatThrownBy(() -> invokeParseMessage(handler, "  "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Empty");
	}

	@Test
	void parseMessage_rejectsNonArrayFormat() {
		GameWebSocketHandler handler = new GameWebSocketHandler(new ChatService(), new GameService());

		assertThatThrownBy(() -> invokeParseMessage(handler, "{\"type\":\"PLAYER_INPUT\"}"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid message format");
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

