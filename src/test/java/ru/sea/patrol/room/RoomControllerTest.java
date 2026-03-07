package ru.sea.patrol.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.sea.patrol.auth.api.dto.AuthResponseDto;
import ru.sea.patrol.service.game.Player;
import ru.sea.patrol.service.game.RoomRegistry;

@SpringBootTest
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RoomControllerTest {

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private RoomRegistry roomRegistry;

	@Test
	void listRooms_withoutToken_returns401() {
		webTestClient
				.get()
				.uri("/api/v1/rooms")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.errors").isArray()
				.jsonPath("$.errors[0].code").isEqualTo("SEAPATROL_UNAUTHORIZED")
				.jsonPath("$.errors[0].message").isEqualTo("Unauthorized");
	}

	@Test
	void listRooms_returnsEmptyCatalog_whenNoRoomsExist() {
		String token = loginAndGetToken("user1", "123456");

		webTestClient
				.get()
				.uri("/api/v1/rooms")
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.maxRooms").isEqualTo(5)
				.jsonPath("$.maxPlayersPerRoom").isEqualTo(100)
				.jsonPath("$.rooms").isArray()
				.jsonPath("$.rooms.length()").isEqualTo(0);
	}

	@Test
	void listRooms_returnsFilledCatalog_whenRoomsExist() {
		String token = loginAndGetToken("user1", "123456");
		String roomId = "sandbox-" + UUID.randomUUID();
		var room = roomRegistry.getOrCreateRoom(roomId);
		room.join(createPlayer("alice"));

		webTestClient
				.get()
				.uri("/api/v1/rooms")
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.rooms.length()").isEqualTo(1)
				.jsonPath("$.rooms[0].id").isEqualTo(roomId)
				.jsonPath("$.rooms[0].name").isEqualTo(roomId)
				.jsonPath("$.rooms[0].mapId").isEqualTo("caribbean-01")
				.jsonPath("$.rooms[0].mapName").isEqualTo("Caribbean Sea")
				.jsonPath("$.rooms[0].currentPlayers").isEqualTo(1)
				.jsonPath("$.rooms[0].maxPlayers").isEqualTo(100)
				.jsonPath("$.rooms[0].status").isEqualTo("OPEN");
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
}

