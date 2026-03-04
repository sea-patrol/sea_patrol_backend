package ru.sea.patrol.ws;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import ru.sea.patrol.MessageType;
import ru.sea.patrol.dto.auth.AuthResponseDto;

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

		String payload = firstMessage.get();
		assertThat(payload).isNotBlank();

		JsonNode root = objectMapper.readTree(payload);
		assertThat(root.isObject()).isTrue();
		assertThat(root.hasNonNull("type")).isTrue();
		assertThat(root.has("payload")).isTrue();

		String typeValue = root.get("type").asText();
		assertThat(typeValue).isNotBlank();
		assertThat(MessageType.valueOf(typeValue)).isNotNull();
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

