package ru.sea.patrol.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "game.room.reconnect-grace-period=120ms"
)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RoomCatalogWsUpdatesTest {

	@LocalServerPort
	private int port;

	@org.springframework.beans.factory.annotation.Autowired
	private WebTestClient webTestClient;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void createRoom_lobbyClientReceivesRoomsUpdated() throws Exception {
		String token = loginAndGetToken("user1", "123456");

		try (ActiveWsConnection connection = openLobbySession(token)) {
			awaitCatalogMessage(connection, MessageType.ROOMS_SNAPSHOT, payload -> payload.path("rooms").isArray(), Duration.ofSeconds(3));

			webTestClient
					.post()
					.uri("/api/v1/rooms")
					.headers(headers -> headers.setBearerAuth(token))
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue("{}")
					.exchange()
					.expectStatus().isCreated();

			JsonNode payload = awaitCatalogMessage(
					connection,
					MessageType.ROOMS_UPDATED,
					catalog -> findRoom(catalog, "sandbox-1") != null,
					Duration.ofSeconds(3)
			);

			JsonNode room = findRoom(payload, "sandbox-1");
			assertThat(room).isNotNull();
			assertThat(room.path("currentPlayers").asInt()).isZero();
			assertThat(room.path("status").asText()).isEqualTo("OPEN");
		}
	}

	@Test
	void joinAndGraceCleanup_lobbyClientReceivesRoomCatalogUpdates() throws Exception {
		String roomPlayerToken = loginAndGetToken("user1", "123456");
		String lobbyObserverToken = loginAndGetToken("user2", "123456");

		try (ActiveWsConnection roomPlayerConnection = openLobbySession(roomPlayerToken)) {
			webTestClient
					.post()
					.uri("/api/v1/rooms")
					.headers(headers -> headers.setBearerAuth(roomPlayerToken))
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue("{}")
					.exchange()
					.expectStatus().isCreated();

			try (ActiveWsConnection lobbyObserverConnection = openLobbySession(lobbyObserverToken)) {
				JsonNode snapshotPayload = awaitCatalogMessage(
						lobbyObserverConnection,
						MessageType.ROOMS_SNAPSHOT,
						catalog -> findRoom(catalog, "sandbox-1") != null,
						Duration.ofSeconds(3)
				);
				assertThat(findRoom(snapshotPayload, "sandbox-1").path("currentPlayers").asInt()).isZero();

				webTestClient
						.post()
						.uri("/api/v1/rooms/{roomId}/join", "sandbox-1")
						.headers(headers -> headers.setBearerAuth(roomPlayerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue("{}")
						.exchange()
						.expectStatus().isOk();

				JsonNode joinUpdatePayload = awaitCatalogMessage(
						lobbyObserverConnection,
						MessageType.ROOMS_UPDATED,
						catalog -> {
							JsonNode room = findRoom(catalog, "sandbox-1");
							return room != null && room.path("currentPlayers").asInt() == 1;
						},
						Duration.ofSeconds(3)
				);
				assertThat(findRoom(joinUpdatePayload, "sandbox-1").path("currentPlayers").asInt()).isEqualTo(1);

				roomPlayerConnection.close();

				JsonNode cleanupPayload = awaitCatalogMessage(
						lobbyObserverConnection,
						MessageType.ROOMS_UPDATED,
						catalog -> findRoom(catalog, "sandbox-1") == null,
						Duration.ofSeconds(3)
				);
				assertThat(findRoom(cleanupPayload, "sandbox-1")).isNull();
			}
		}
	}

	private ActiveWsConnection openLobbySession(String token) throws Exception {
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
		assertThat(error.get()).isNull();
		return new ActiveWsConnection(release, disposable, finished, error, messages);
	}

	private JsonNode awaitCatalogMessage(
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
		throw new AssertionError("Timed out waiting for catalog WS message type " + expectedType);
	}

	private static JsonNode findRoom(JsonNode catalogPayload, String roomId) {
		for (JsonNode room : catalogPayload.path("rooms")) {
			if (roomId.equals(room.path("id").asText())) {
				return room;
			}
		}
		return null;
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
		private final Disposable disposable;
		private final CountDownLatch finished;
		private final AtomicReference<Throwable> error;
		private final LinkedBlockingQueue<String> messages;
		private boolean closed;

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
