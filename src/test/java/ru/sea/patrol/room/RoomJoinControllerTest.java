package ru.sea.patrol.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import ru.sea.patrol.service.game.Player;
import ru.sea.patrol.service.game.RoomRegistry;
import ru.sea.patrol.service.game.RoomRegistryEntry;
import ru.sea.patrol.ws.protocol.MessageType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RoomJoinControllerTest {

	@LocalServerPort
	private int port;

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private RoomRegistry roomRegistry;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void joinRoom_withoutLobbyWsSession_returns409() {
		String token = loginAndGetToken("user1", "123456");
		RoomRegistryEntry room = roomRegistry.createRoom("Sandbox 1", "caribbean-01", "Caribbean Sea");

		webTestClient
				.post()
				.uri("/api/v1/rooms/{roomId}/join", room.id())
				.headers(headers -> headers.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isEqualTo(409)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.errors[0].code").isEqualTo("LOBBY_SESSION_REQUIRED")
				.jsonPath("$.errors[0].message").isEqualTo("Active lobby WebSocket session is required");
	}

	@Test
	void joinRoom_notFound_withLobbyWsSession_returns404() throws Exception {
		String token = loginAndGetToken("user1", "123456");

		try (ActiveWsConnection connection = openSession(token)) {
			webTestClient
					.post()
					.uri("/api/v1/rooms/{roomId}/join", "missing-room")
					.headers(headers -> headers.setBearerAuth(token))
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue("{}")
					.exchange()
					.expectStatus().isNotFound()
					.expectBody()
					.jsonPath("$.errors[0].code").isEqualTo("ROOM_NOT_FOUND")
					.jsonPath("$.errors[0].message").isEqualTo("Room not found");
		}
	}

	@Test
	void joinRoom_fullRoom_withLobbyWsSession_returns409() throws Exception {
		String token = loginAndGetToken("user1", "123456");
		RoomRegistryEntry room = roomRegistry.createRoom("Sandbox Full", "caribbean-01", "Caribbean Sea");
		var gameRoom = roomRegistry.findRoom(room.id());
		for (int i = 0; i < 100; i++) {
			gameRoom.join(createPlayer("bot-" + i));
		}

		try (ActiveWsConnection connection = openSession(token)) {
			webTestClient
					.post()
					.uri("/api/v1/rooms/{roomId}/join", room.id())
					.headers(headers -> headers.setBearerAuth(token))
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue("{}")
					.exchange()
					.expectStatus().isEqualTo(409)
					.expectBody()
					.jsonPath("$.errors[0].code").isEqualTo("ROOM_FULL")
					.jsonPath("$.errors[0].message").isEqualTo("Room is full");
		}
	}

	@Test
	void joinRoom_success_returns200_andWsReceivesJoinSequence() throws Exception {
		String token = loginAndGetToken("user1", "123456");
		RoomRegistryEntry room = roomRegistry.createRoom("Sandbox 7", "test-sandbox-01", "Test Sandbox");

		try (ActiveWsConnection connection = openSession(token)) {
			webTestClient
					.post()
					.uri("/api/v1/rooms/{roomId}/join", room.id())
					.headers(headers -> headers.setBearerAuth(token))
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue("{}")
					.exchange()
					.expectStatus().isOk()
					.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
					.expectBody()
					.jsonPath("$.roomId").isEqualTo(room.id())
					.jsonPath("$.mapId").isEqualTo("test-sandbox-01")
					.jsonPath("$.mapName").isEqualTo("Test Sandbox")
					.jsonPath("$.currentPlayers").isEqualTo(1)
					.jsonPath("$.maxPlayers").isEqualTo(100)
					.jsonPath("$.status").isEqualTo("JOINED");

			JsonNode joinedMessage = awaitMessageOfType(connection, MessageType.ROOM_JOINED, Duration.ofSeconds(3));
			assertThat(joinedMessage.path("payload").path("roomId").asText()).isEqualTo(room.id());
			assertThat(joinedMessage.path("payload").path("status").asText()).isEqualTo("JOINED");

			JsonNode spawnAssignedMessage = awaitMessageOfType(connection, MessageType.SPAWN_ASSIGNED, Duration.ofSeconds(3));
			double spawnX = spawnAssignedMessage.path("payload").path("x").asDouble();
			double spawnZ = spawnAssignedMessage.path("payload").path("z").asDouble();
			double spawnAngle = spawnAssignedMessage.path("payload").path("angle").asDouble();
			assertThat(spawnAssignedMessage.path("payload").path("roomId").asText()).isEqualTo(room.id());
			assertThat(spawnAssignedMessage.path("payload").path("reason").asText()).isEqualTo("INITIAL");
			assertThat(spawnX).isBetween(-5.0, 25.0);
			assertThat(spawnZ).isBetween(-25.0, 5.0);
			assertThat(spawnAngle).isEqualTo(0.5);

			JsonNode initMessage = awaitMessageOfType(connection, MessageType.INIT_GAME_STATE, Duration.ofSeconds(3));
			JsonNode roomMeta = initMessage.path("payload").path("roomMeta");
			JsonNode currentPlayer = initMessage.path("payload").path("players").get(0);
			assertThat(initMessage.path("payload").path("room").asText()).isEqualTo(room.id());
			assertThat(roomMeta.path("roomId").asText()).isEqualTo(room.id());
			assertThat(roomMeta.path("roomName").asText()).isEqualTo("Sandbox 7");
			assertThat(roomMeta.path("mapId").asText()).isEqualTo("test-sandbox-01");
			assertThat(roomMeta.path("mapName").asText()).isEqualTo("Test Sandbox");
			assertThat(roomMeta.path("theme").asText()).isEqualTo("debug");
			assertThat(roomMeta.path("bounds").path("minX").asDouble()).isEqualTo(-1000.0);
			assertThat(roomMeta.path("bounds").path("maxX").asDouble()).isEqualTo(1000.0);
			assertThat(initMessage.path("payload").path("wind").path("angle").asDouble()).isCloseTo(1.57, org.assertj.core.data.Offset.offset(0.01));
			assertThat(initMessage.path("payload").path("wind").path("speed").asDouble()).isEqualTo(4.0);
			assertThat(currentPlayer.path("name").asText()).isEqualTo("user1");
			assertThat(currentPlayer.path("sailLevel").asInt()).isEqualTo(3);
			assertThat(currentPlayer.path("x").asDouble()).isCloseTo(spawnX, org.assertj.core.data.Offset.offset(0.0001));
			assertThat(currentPlayer.path("z").asDouble()).isCloseTo(spawnZ, org.assertj.core.data.Offset.offset(0.0001));
			assertThat(currentPlayer.path("angle").asDouble()).isCloseTo(spawnAngle, org.assertj.core.data.Offset.offset(0.0001));

			JsonNode updateMessage = awaitMessageOfType(connection, MessageType.UPDATE_GAME_STATE, Duration.ofSeconds(3));
			assertThat(updateMessage.path("payload").path("wind").path("angle").asDouble()).isLessThan(1.57);
			assertThat(updateMessage.path("payload").path("wind").path("angle").asDouble()).isGreaterThanOrEqualTo(0.0);
			assertThat(updateMessage.path("payload").path("wind").path("speed").asDouble()).isEqualTo(4.0);
			assertThat(updateMessage.path("payload").path("players")).hasSize(1);
			assertThat(updateMessage.path("payload").path("players").get(0).path("sailLevel").asInt()).isEqualTo(3);
		}
	}

	@Test
	void reconnectWithinGrace_resumesSameRoomWithoutNewSpawn() throws Exception {
		String token = loginAndGetToken("user1", "123456");
		RoomRegistryEntry room = roomRegistry.createRoom("Sandbox Resume", "caribbean-01", "Caribbean Sea");
		double spawnX;
		double spawnZ;
		double spawnAngle;
		double retainedX;
		double retainedZ;
		double retainedAngle;

		try (ActiveWsConnection connection = openSession(token)) {
			webTestClient
					.post()
					.uri("/api/v1/rooms/{roomId}/join", room.id())
					.headers(headers -> headers.setBearerAuth(token))
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue("{}")
					.exchange()
					.expectStatus().isOk();

			awaitMessageOfType(connection, MessageType.ROOM_JOINED, Duration.ofSeconds(3));
			JsonNode spawnAssignedMessage = awaitMessageOfType(connection, MessageType.SPAWN_ASSIGNED, Duration.ofSeconds(3));
			spawnX = spawnAssignedMessage.path("payload").path("x").asDouble();
			spawnZ = spawnAssignedMessage.path("payload").path("z").asDouble();
			spawnAngle = spawnAssignedMessage.path("payload").path("angle").asDouble();
			JsonNode initMessage = awaitMessageOfType(connection, MessageType.INIT_GAME_STATE, Duration.ofSeconds(3));
			JsonNode initialPlayer = initMessage.path("payload").path("players").get(0);
			assertThat(initialPlayer.path("x").asDouble()).isCloseTo(spawnX, org.assertj.core.data.Offset.offset(0.0001));
			assertThat(initialPlayer.path("z").asDouble()).isCloseTo(spawnZ, org.assertj.core.data.Offset.offset(0.0001));
			assertThat(initialPlayer.path("angle").asDouble()).isCloseTo(spawnAngle, org.assertj.core.data.Offset.offset(0.0001));
			JsonNode updateMessage = awaitMessageOfType(connection, MessageType.UPDATE_GAME_STATE, Duration.ofSeconds(3));
			JsonNode retainedPlayer = updateMessage.path("payload").path("players").get(0);
			retainedX = retainedPlayer.path("x").asDouble();
			retainedZ = retainedPlayer.path("z").asDouble();
			retainedAngle = retainedPlayer.path("angle").asDouble();
		}

		try (ActiveWsConnection resumedConnection = openSession(token)) {
			List<MessageType> seenTypes = new ArrayList<>();
			JsonNode resumedJoinMessage = awaitMessageOfType(resumedConnection, MessageType.ROOM_JOINED, Duration.ofSeconds(3), seenTypes);
			assertThat(resumedJoinMessage.path("payload").path("roomId").asText()).isEqualTo(room.id());

			JsonNode resumedInitMessage = awaitMessageOfType(resumedConnection, MessageType.INIT_GAME_STATE, Duration.ofSeconds(3), seenTypes);
			JsonNode resumedPlayer = resumedInitMessage.path("payload").path("players").get(0);
			assertThat(seenTypes).doesNotContain(MessageType.SPAWN_ASSIGNED, MessageType.ROOMS_SNAPSHOT);
			assertThat(resumedInitMessage.path("payload").path("room").asText()).isEqualTo(room.id());
			assertThat(resumedInitMessage.path("payload").path("roomMeta").path("mapId").asText()).isEqualTo("caribbean-01");
			assertThat(resumedPlayer.path("name").asText()).isEqualTo("user1");
			assertThat(resumedPlayer.path("sailLevel").asInt()).isEqualTo(3);
			assertThat(resumedPlayer.path("x").asDouble()).isCloseTo(retainedX, org.assertj.core.data.Offset.offset(0.0001));
			assertThat(resumedPlayer.path("z").asDouble()).isCloseTo(retainedZ, org.assertj.core.data.Offset.offset(0.0001));
			assertThat(resumedPlayer.path("angle").asDouble()).isCloseTo(retainedAngle, org.assertj.core.data.Offset.offset(0.0001));
		}
	}

	private ActiveWsConnection openSession(String token) throws Exception {
		URI uri = buildWebSocketUri(token);
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
		LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
		AtomicReference<Throwable> error = new AtomicReference<>();
		CountDownLatch firstMessageReceived = new CountDownLatch(1);
		CountDownLatch finished = new CountDownLatch(1);
		Sinks.Empty<Void> release = Sinks.empty();

		Disposable disposable = client.execute(
						uri,
						session -> session.receive()
								.map(WebSocketMessage::getPayloadAsText)
								.doOnNext(payload -> {
									messages.offer(payload);
									firstMessageReceived.countDown();
								})
								.takeUntilOther(release.asMono())
								.then(session.close()))
				.doOnError(error::set)
				.doFinally(signal -> finished.countDown())
				.subscribe();

		assertThat(firstMessageReceived.await(3, TimeUnit.SECONDS)).isTrue();
		assertThat(messages.peek()).isNotBlank();
		assertThat(error.get()).isNull();
		return new ActiveWsConnection(release, disposable, finished, error, messages);
	}

	private JsonNode awaitMessageOfType(ActiveWsConnection connection, MessageType expectedType, Duration timeout) throws Exception {
		return awaitMessageOfType(connection, expectedType, timeout, new ArrayList<>());
	}

	private JsonNode awaitMessageOfType(
			ActiveWsConnection connection,
			MessageType expectedType,
			Duration timeout,
			List<MessageType> seenTypes
	) throws Exception {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			long remainingMillis = Math.max(1L, (deadline - System.nanoTime()) / 1_000_000L);
			String payload = connection.messages.poll(remainingMillis, TimeUnit.MILLISECONDS);
			if (payload == null) {
				continue;
			}
			JsonNode root = objectMapper.readTree(payload);
			MessageType receivedType = MessageType.valueOf(root.path("type").asText());
			seenTypes.add(receivedType);
			if (expectedType == receivedType) {
				return root;
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
						  \"username\": \"%s\",
						  \"password\": \"%s\"
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

	private static Player createPlayer(String name) {
		return new Player(name)
				.setModel("model")
				.setMaxHealth(500)
				.setHealth(500)
				.setAngle(0)
				.setVelocity(0)
				.setX(0)
				.setZ(0)
				.setHeight(4f)
				.setWidth(7f)
				.setLength(26f);
	}

	private static final class ActiveWsConnection implements AutoCloseable {
		private final Sinks.Empty<Void> release;
		private final Disposable disposable;
		private final CountDownLatch finished;
		private final AtomicReference<Throwable> error;
		private final LinkedBlockingQueue<String> messages;

		private ActiveWsConnection(
				Sinks.Empty<Void> release,
				Disposable disposable,
				CountDownLatch finished,
				AtomicReference<Throwable> error,
				LinkedBlockingQueue<String> messages
		) {
			this.release = release;
			this.disposable = disposable;
			this.finished = finished;
			this.error = error;
			this.messages = messages;
		}

		@Override
		public void close() throws Exception {
			release.tryEmitEmpty();
			assertThat(finished.await(3, TimeUnit.SECONDS)).isTrue();
			disposable.dispose();
			assertThat(error.get()).isNull();
		}
	}
}
