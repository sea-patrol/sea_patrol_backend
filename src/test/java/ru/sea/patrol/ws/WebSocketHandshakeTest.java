package ru.sea.patrol.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import ru.sea.patrol.auth.api.dto.AuthResponseDto;
import ru.sea.patrol.service.session.GameSessionRegistry;
import ru.sea.patrol.ws.protocol.MessageType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class WebSocketHandshakeTest {

	@LocalServerPort
	private int port;

	@Autowired
	private WebTestClient webTestClient;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void wsHandshake_withValidJwt_receivesValidMessageEnvelope() throws Exception {
		String token = loginAndGetToken("user1", "123456");
		String payload = connectAndReceiveFirstMessage(token);
		assertThat(payload).isNotBlank();

		JsonNode root = objectMapper.readTree(payload);
		assertThat(root.isObject()).isTrue();
		assertThat(root.hasNonNull("type")).isTrue();
		assertThat(root.has("payload")).isTrue();

		String typeValue = root.get("type").asText();
		assertThat(typeValue).isNotBlank();
		assertThat(MessageType.valueOf(typeValue)).isNotNull();
	}

	@Test
	void wsHandshake_reconnectsSeveralTimes_doesNotHang_andReceivesMessages() throws Exception {
		String token = loginAndGetToken("user1", "123456");

		for (int i = 0; i < 3; i++) {
			String payload = connectAndReceiveFirstMessage(token);
			assertThat(payload).isNotBlank();
			Thread.sleep(75L);
		}
	}

	@Test
	void duplicateLogin_whileWsSessionActive_returnsStructuredError() throws Exception {
		String token = loginAndGetToken("user1", "123456");

		try (ActiveConnection ignored = openAndHoldSession(token)) {
			webTestClient
					.post()
					.uri("/api/v1/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue("""
							{
							  "username": "user1",
							  "password": "123456"
							}
							""")
					.exchange()
					.expectStatus().isUnauthorized()
					.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
					.expectBody()
					.jsonPath("$.errors[0].code").isEqualTo(GameSessionRegistry.DUPLICATE_SESSION_ERROR_CODE)
					.jsonPath("$.errors[0].message").isEqualTo(GameSessionRegistry.DUPLICATE_SESSION_MESSAGE);
		}
	}

	@Test
	void loginAfterWsSessionCloses_isAllowedAgain() throws Exception {
		String token = loginAndGetToken("user1", "123456");

		ActiveConnection connection = openAndHoldSession(token);
		connection.close();

		webTestClient
				.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  \"username\": \"user1\",
						  \"password\": \"123456\"
						}
						""")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody(AuthResponseDto.class)
				.value(response -> assertThat(response.getToken()).isNotBlank());
	}

	@Test
	void duplicateParallelWsConnection_isRejectedWithPolicyViolation() throws Exception {
		String token = loginAndGetToken("user1", "123456");

		try (ActiveConnection ignored = openAndHoldSession(token)) {
			CloseStatus closeStatus = connectAndCaptureCloseStatus(token);
			assertThat(closeStatus).isNotNull();
			assertThat(closeStatus.getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
			assertThat(closeStatus.getReason()).contains(GameSessionRegistry.DUPLICATE_SESSION_ERROR_CODE);
		}
	}

	private ActiveConnection openAndHoldSession(String token) throws Exception {
		URI uri = buildWebSocketUri(token);
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
		AtomicReference<String> firstMessage = new AtomicReference<>();
		AtomicReference<Throwable> error = new AtomicReference<>();
		CountDownLatch firstMessageReceived = new CountDownLatch(1);
		CountDownLatch finished = new CountDownLatch(1);
		Sinks.Empty<Void> release = Sinks.empty();

		Disposable disposable = client.execute(
						uri,
						session -> session.receive()
								.map(WebSocketMessage::getPayloadAsText)
								.doOnNext(payload -> {
									if (firstMessage.compareAndSet(null, payload)) {
										firstMessageReceived.countDown();
									}
								})
								.takeUntilOther(release.asMono())
								.then(session.close()))
				.doOnError(error::set)
				.doFinally(signal -> finished.countDown())
				.subscribe();

		assertThat(firstMessageReceived.await(3, TimeUnit.SECONDS)).isTrue();
		assertThat(firstMessage.get()).isNotBlank();
		assertThat(error.get()).isNull();
		return new ActiveConnection(release, disposable, finished, error);
	}

	private CloseStatus connectAndCaptureCloseStatus(String token) {
		URI uri = buildWebSocketUri(token);
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
		AtomicReference<CloseStatus> closeStatus = new AtomicReference<>();

		client.execute(uri, session -> session.closeStatus().doOnNext(closeStatus::set).then())
				.block(Duration.ofSeconds(5));

		return closeStatus.get();
	}

	private String connectAndReceiveFirstMessage(String token) {
		URI uri = buildWebSocketUri(token);
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
		AtomicReference<String> firstMessage = new AtomicReference<>();

		client.execute(
						uri,
						session -> session
								.receive()
								.map(WebSocketMessage::getPayloadAsText)
								.next()
								.timeout(Duration.ofSeconds(3))
								.doOnNext(firstMessage::set)
								.then(session.close()))
				.block(Duration.ofSeconds(5));

		return firstMessage.get();
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

	private static final class ActiveConnection implements AutoCloseable {
		private final Sinks.Empty<Void> release;
		private final Disposable disposable;
		private final CountDownLatch finished;
		private final AtomicReference<Throwable> error;

		private ActiveConnection(Sinks.Empty<Void> release, Disposable disposable, CountDownLatch finished, AtomicReference<Throwable> error) {
			this.release = release;
			this.disposable = disposable;
			this.finished = finished;
			this.error = error;
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


