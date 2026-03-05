package ru.sea.patrol.ws;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import ru.sea.patrol.ws.protocol.MessageType;
import ru.sea.patrol.auth.api.dto.AuthResponseDto;

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
	void wsHandshake_reconnectsSeveralTimes_doesNotHang_andReceivesMessages() {
		String token = loginAndGetToken("user1", "123456");

		for (int i = 0; i < 3; i++) {
			String payload = connectAndReceiveFirstMessage(token);
			assertThat(payload).isNotBlank();
		}
	}

	private String connectAndReceiveFirstMessage(String token) {
		String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
		URI uri = URI.create("ws://localhost:%d/ws/game?token=%s".formatted(port, encodedToken));

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
}
