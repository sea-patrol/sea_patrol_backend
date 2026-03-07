package ru.sea.patrol.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import ru.sea.patrol.auth.api.dto.AuthResponseDto;
import ru.sea.patrol.ws.protocol.MessageType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RoomChatIsolationWsTest {

	@LocalServerPort
	private int port;

	@org.springframework.beans.factory.annotation.Autowired
	private WebTestClient webTestClient;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void roomPlayerDoesNotReceiveLobbyChat_andLobbyPlayerDoesNotReceiveRoomChat() throws Exception {
		String roomPlayerToken = loginAndGetToken("user1", "123456");
		String lobbyPlayerToken = loginAndGetToken("user2", "123456");
		String roomId = createRoom(roomPlayerToken).path("id").asText();

		try (ActiveWsConnection roomPlayer = openSession(roomPlayerToken);
				 ActiveWsConnection lobbyPlayer = openSession(lobbyPlayerToken)) {
			awaitMessageOfType(roomPlayer, MessageType.ROOMS_SNAPSHOT, payload -> payload.path("rooms").isArray(), Duration.ofSeconds(3));
			awaitMessageOfType(lobbyPlayer, MessageType.ROOMS_SNAPSHOT, payload -> payload.path("rooms").isArray(), Duration.ofSeconds(3));

			joinRoom(roomPlayerToken, roomId);
			awaitMessageOfType(roomPlayer, MessageType.ROOM_JOINED, payload -> roomId.equals(payload.path("roomId").asText()), Duration.ofSeconds(3));
			awaitMessageOfType(roomPlayer, MessageType.SPAWN_ASSIGNED, payload -> roomId.equals(payload.path("roomId").asText()), Duration.ofSeconds(3));
			roomPlayer.clearMessages();
			lobbyPlayer.clearMessages();

			lobbyPlayer.send(MessageType.CHAT_MESSAGE, Map.of("to", "group:lobby", "text", "lobby only"));
			JsonNode lobbyMessage = awaitMessageOfType(
					lobbyPlayer,
					MessageType.CHAT_MESSAGE,
					payload -> "lobby only".equals(payload.path("text").asText()),
					Duration.ofSeconds(3)
			);
			assertThat(lobbyMessage.path("from").asText()).isEqualTo("user2");
			assertThat(lobbyMessage.path("to").asText()).isEqualTo("group:lobby");
			roomPlayer.assertNoMessage(
					MessageType.CHAT_MESSAGE,
					payload -> "lobby only".equals(payload.path("text").asText()),
					Duration.ofMillis(350)
			);

			roomPlayer.send(MessageType.CHAT_MESSAGE, Map.of("to", "group:lobby", "text", "room only"));
			JsonNode roomMessage = awaitMessageOfType(
					roomPlayer,
					MessageType.CHAT_MESSAGE,
					payload -> "room only".equals(payload.path("text").asText()),
					Duration.ofSeconds(3)
			);
			assertThat(roomMessage.path("from").asText()).isEqualTo("user1");
			assertThat(roomMessage.path("to").asText()).isEqualTo("group:room:" + roomId);
			lobbyPlayer.assertNoMessage(
					MessageType.CHAT_MESSAGE,
					payload -> "room only".equals(payload.path("text").asText()),
					Duration.ofMillis(350)
			);
		}
	}

	@Test
	void roomPlayersInDifferentRooms_doNotReceiveEachOthersMessages() throws Exception {
		String firstRoomToken = loginAndGetToken("user1", "123456");
		String secondRoomToken = loginAndGetToken("user2", "123456");
		String firstRoomId = createRoom(firstRoomToken).path("id").asText();
		String secondRoomId = createRoom(firstRoomToken).path("id").asText();

		try (ActiveWsConnection firstRoomPlayer = openSession(firstRoomToken);
				 ActiveWsConnection secondRoomPlayer = openSession(secondRoomToken)) {
			awaitMessageOfType(firstRoomPlayer, MessageType.ROOMS_SNAPSHOT, payload -> payload.path("rooms").isArray(), Duration.ofSeconds(3));
			awaitMessageOfType(secondRoomPlayer, MessageType.ROOMS_SNAPSHOT, payload -> payload.path("rooms").isArray(), Duration.ofSeconds(3));

			joinRoom(firstRoomToken, firstRoomId);
			awaitMessageOfType(firstRoomPlayer, MessageType.ROOM_JOINED, payload -> firstRoomId.equals(payload.path("roomId").asText()), Duration.ofSeconds(3));
			awaitMessageOfType(firstRoomPlayer, MessageType.SPAWN_ASSIGNED, payload -> firstRoomId.equals(payload.path("roomId").asText()), Duration.ofSeconds(3));

			joinRoom(secondRoomToken, secondRoomId);
			awaitMessageOfType(secondRoomPlayer, MessageType.ROOM_JOINED, payload -> secondRoomId.equals(payload.path("roomId").asText()), Duration.ofSeconds(3));
			awaitMessageOfType(secondRoomPlayer, MessageType.SPAWN_ASSIGNED, payload -> secondRoomId.equals(payload.path("roomId").asText()), Duration.ofSeconds(3));
			firstRoomPlayer.clearMessages();
			secondRoomPlayer.clearMessages();

			firstRoomPlayer.send(MessageType.CHAT_MESSAGE, Map.of("to", "group:room:" + secondRoomId, "text", "room one only"));
			JsonNode firstRoomMessage = awaitMessageOfType(
					firstRoomPlayer,
					MessageType.CHAT_MESSAGE,
					payload -> "room one only".equals(payload.path("text").asText()),
					Duration.ofSeconds(3)
			);
			assertThat(firstRoomMessage.path("to").asText()).isEqualTo("group:room:" + firstRoomId);
			secondRoomPlayer.assertNoMessage(
					MessageType.CHAT_MESSAGE,
					payload -> "room one only".equals(payload.path("text").asText()),
					Duration.ofMillis(350)
			);
		}
	}

	private JsonNode createRoom(String token) {
		return webTestClient
				.post()
				.uri("/api/v1/rooms")
				.headers(headers -> headers.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isCreated()
				.expectBody(JsonNode.class)
				.returnResult()
				.getResponseBody();
	}

	private void joinRoom(String token, String roomId) {
		webTestClient
				.post()
				.uri("/api/v1/rooms/{roomId}/join", roomId)
				.headers(headers -> headers.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isOk();
	}

	private ActiveWsConnection openSession(String token) throws Exception {
		URI uri = buildWebSocketUri(token);
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
		LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
		AtomicReference<Throwable> error = new AtomicReference<>();
		CountDownLatch firstMessageReceived = new CountDownLatch(1);
		CountDownLatch finished = new CountDownLatch(1);
		Sinks.Empty<Void> release = Sinks.empty();
		Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();

		Disposable disposable = client.execute(
						uri,
						session -> {
							var outboundFlux = outbound.asFlux()
									.takeUntilOther(release.asMono())
									.map(session::textMessage);
							var sendFlow = session.send(outboundFlux);
							var receiveFlow = session.receive()
									.map(WebSocketMessage::getPayloadAsText)
									.doOnNext(payload -> {
										messages.offer(payload);
										firstMessageReceived.countDown();
									})
									.takeUntilOther(release.asMono())
									.then();
							return sendFlow.and(receiveFlow).then(session.close());
						})
				.doOnError(error::set)
				.doFinally(signal -> finished.countDown())
				.subscribe();

		assertThat(firstMessageReceived.await(3, TimeUnit.SECONDS)).isTrue();
		assertThat(error.get()).isNull();
		return new ActiveWsConnection(release, outbound, disposable, finished, error, messages, objectMapper);
	}

	private JsonNode awaitMessageOfType(
			ActiveWsConnection connection,
			MessageType expectedType,
			Predicate<JsonNode> payloadPredicate,
			Duration timeout
	) throws Exception {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			long remainingMillis = Math.max(1L, (deadline - System.nanoTime()) / 1_000_000L);
			String payload = connection.messages.poll(remainingMillis, TimeUnit.MILLISECONDS);
			if (payload == null) {
				continue;
			}
			JsonNode root = objectMapper.readTree(payload);
			if (!expectedType.name().equals(root.path("type").asText())) {
				continue;
			}
			JsonNode messagePayload = root.path("payload");
			if (payloadPredicate.test(messagePayload)) {
				return messagePayload;
			}
		}
		throw new AssertionError("Timed out waiting for WS message type " + expectedType);
	}

	private URI buildWebSocketUri(String token) {
		String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
		return URI.create("ws://localhost:%d/ws/game?token=%s".formatted(port, encodedToken));
	}

	private String loginAndGetToken(String username, String password) {
		AuthResponseDto response = webTestClient
				.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "username": "%s",
						  "password": "%s"
						}
						""".formatted(username, password))
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody(AuthResponseDto.class)
				.returnResult()
				.getResponseBody();

		assertThat(response).isNotNull();
		assertThat(response.getToken()).isNotBlank();
		return response.getToken();
	}

	private static final class ActiveWsConnection implements AutoCloseable {
		private final Sinks.Empty<Void> release;
		private final Sinks.Many<String> outbound;
		private final Disposable disposable;
		private final CountDownLatch finished;
		private final AtomicReference<Throwable> error;
		private final LinkedBlockingQueue<String> messages;
		private final ObjectMapper objectMapper;
		private boolean closed;

		private ActiveWsConnection(
				Sinks.Empty<Void> release,
				Sinks.Many<String> outbound,
				Disposable disposable,
				CountDownLatch finished,
				AtomicReference<Throwable> error,
				LinkedBlockingQueue<String> messages,
				ObjectMapper objectMapper
		) {
			this.release = release;
			this.outbound = outbound;
			this.disposable = disposable;
			this.finished = finished;
			this.error = error;
			this.messages = messages;
			this.objectMapper = objectMapper;
		}

		private void send(MessageType type, Object payload) throws Exception {
			String serialized = objectMapper.writeValueAsString(new Object[] {type.name(), payload});
			assertThat(outbound.tryEmitNext(serialized).isSuccess()).isTrue();
		}

		private void clearMessages() {
			messages.clear();
		}

		private void assertNoMessage(MessageType type, Predicate<JsonNode> payloadPredicate, Duration timeout) {
			assertThatThrownBy(() -> {
				long deadline = System.nanoTime() + timeout.toNanos();
				while (System.nanoTime() < deadline) {
					long remainingMillis = Math.max(1L, (deadline - System.nanoTime()) / 1_000_000L);
					try {
						String payload = messages.poll(remainingMillis, TimeUnit.MILLISECONDS);
						if (payload == null) {
							continue;
						}
						JsonNode root = objectMapper.readTree(payload);
						if (!type.name().equals(root.path("type").asText())) {
							continue;
						}
						JsonNode messagePayload = root.path("payload");
						if (payloadPredicate.test(messagePayload)) {
							throw new AssertionError("Unexpected matching WS message observed: " + messagePayload);
						}
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
						throw new AssertionError("Interrupted while checking WS messages", exception);
					} catch (Exception exception) {
						throw new AssertionError("Failed to inspect WS payload", exception);
					}
				}
				throw new IllegalStateException(new TimeoutException("No matching WS message observed"));
			}).isInstanceOf(IllegalStateException.class)
				.hasCauseInstanceOf(TimeoutException.class);
		}

		@Override
		public void close() throws Exception {
			if (closed) {
				return;
			}
			closed = true;
			release.tryEmitEmpty();
			assertThat(finished.await(3, TimeUnit.SECONDS)).isTrue();
			disposable.dispose();
			assertThat(error.get()).isNull();
		}
	}
}



