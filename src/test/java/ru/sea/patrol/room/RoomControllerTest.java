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
	void createRoom_withoutToken_returns401() {
		webTestClient
				.post()
				.uri("/api/v1/rooms")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.errors[0].code").isEqualTo("SEAPATROL_UNAUTHORIZED");
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
	void createRoom_returnsCreatedResponse_withGeneratedRoomIdentity() {
		String token = loginAndGetToken("user1", "123456");

		webTestClient
				.post()
				.uri("/api/v1/rooms")
				.headers(headers -> headers.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isCreated()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.id").isEqualTo("sandbox-1")
				.jsonPath("$.name").isEqualTo("Sandbox 1")
				.jsonPath("$.mapId").isEqualTo("caribbean-01")
				.jsonPath("$.mapName").isEqualTo("Caribbean Sea")
				.jsonPath("$.currentPlayers").isEqualTo(0)
				.jsonPath("$.maxPlayers").isEqualTo(100)
				.jsonPath("$.status").isEqualTo("OPEN");
	}

	@Test
	void createRoom_withCustomName_slugifiesId_andPreservesDisplayName() {
		String token = loginAndGetToken("user1", "123456");

		webTestClient
				.post()
				.uri("/api/v1/rooms")
				.headers(headers -> headers.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "name": "Sandbox 3",
						  "mapId": "caribbean-01"
						}
						""")
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.id").isEqualTo("sandbox-3")
				.jsonPath("$.name").isEqualTo("Sandbox 3")
				.jsonPath("$.mapId").isEqualTo("caribbean-01");
	}

	@Test
	void createRoom_withRegisteredDevMap_returnsCreatedResponse() {
		String token = loginAndGetToken("user1", "123456");

		webTestClient
				.post()
				.uri("/api/v1/rooms")
				.headers(headers -> headers.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "name": "Debug Room",
						  "mapId": "test-sandbox-01"
						}
						""")
				.exchange()
				.expectStatus().isCreated()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.id").isEqualTo("debug-room")
				.jsonPath("$.name").isEqualTo("Debug Room")
				.jsonPath("$.mapId").isEqualTo("test-sandbox-01")
				.jsonPath("$.mapName").isEqualTo("Test Sandbox")
				.jsonPath("$.status").isEqualTo("OPEN");
	}

	@Test
	void createRoom_invalidMapId_returns400() {
		String token = loginAndGetToken("user1", "123456");

		webTestClient
				.post()
				.uri("/api/v1/rooms")
				.headers(headers -> headers.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "mapId": "japan-01"
						}
						""")
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.errors[0].code").isEqualTo("INVALID_MAP_ID")
				.jsonPath("$.errors[0].message").isEqualTo("Unknown mapId");
	}

	@Test
	void createRoom_whenMaxRoomsReached_returns409() {
		String token = loginAndGetToken("user1", "123456");
		for (int i = 0; i < 5; i++) {
			roomRegistry.createRoom(null, "caribbean-01", "Caribbean Sea");
		}

		webTestClient
				.post()
				.uri("/api/v1/rooms")
				.headers(headers -> headers.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isEqualTo(409)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.errors[0].code").isEqualTo("MAX_ROOMS_REACHED")
				.jsonPath("$.errors[0].message").isEqualTo("Maximum number of rooms reached");
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