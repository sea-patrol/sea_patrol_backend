package ru.sea.patrol.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import ru.sea.patrol.service.game.GameRoomProperties;
import ru.sea.patrol.service.game.GameService;
import ru.sea.patrol.service.game.RoomCatalogService;
import ru.sea.patrol.service.game.RoomCatalogWsService;
import ru.sea.patrol.service.game.RoomRegistry;
import ru.sea.patrol.service.game.RoomRegistryEntry;
import ru.sea.patrol.service.session.GameSessionRegistry;
import tools.jackson.databind.ObjectMapper;

class GameRoomCleanupPolicyTest {

	@Test
	void cleanupPlayer_removesEmptyRoomImmediately_withoutReconnectGrace() {
		GameRoomProperties properties = properties(Duration.ofSeconds(30));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		RoomCatalogService roomCatalogService = new RoomCatalogService(roomRegistry, properties);
		RoomCatalogWsService roomCatalogWsService = new RoomCatalogWsService(roomCatalogService);
		GameSessionRegistry sessionRegistry = new GameSessionRegistry(properties, roomRegistry, roomCatalogWsService);
		GameService gameService = new GameService(new ObjectMapper(), properties, roomRegistry, sessionRegistry);
		RoomRegistryEntry room = roomRegistry.createRoom("Sandbox 1", "caribbean-01", "Caribbean Sea");

		gameService.prepareRoomJoin("alice", room.id());
		gameService.cleanupPlayer("alice");

		assertThat(roomRegistry.hasRoom(room.id())).isFalse();
		sessionRegistry.shutdown();
	}

	@Test
	void cleanupPlayer_keepsEmptyRoomWhileReconnectGraceExists_andRemovesAfterExpiration() throws Exception {
		GameRoomProperties properties = properties(Duration.ofMillis(120));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		RoomCatalogService roomCatalogService = new RoomCatalogService(roomRegistry, properties);
		RoomCatalogWsService roomCatalogWsService = new RoomCatalogWsService(roomCatalogService);
		GameSessionRegistry sessionRegistry = new GameSessionRegistry(properties, roomRegistry, roomCatalogWsService);
		GameService gameService = new GameService(new ObjectMapper(), properties, roomRegistry, sessionRegistry);
		RoomRegistryEntry room = roomRegistry.createRoom("Sandbox 2", "caribbean-01", "Caribbean Sea");

		sessionRegistry.claimSession("alice", "s1");
		gameService.prepareRoomJoin("alice", room.id());
		assertThat(sessionRegistry.bindToRoom("alice", room.id())).isTrue();

		sessionRegistry.registerDisconnect("alice", "s1");
		gameService.cleanupPlayer("alice");

		assertThat(sessionRegistry.hasReconnectGraceInRoom(room.id())).isTrue();
		assertThat(roomRegistry.hasRoom(room.id())).isTrue();

		Thread.sleep(250L);

		assertThat(sessionRegistry.hasReconnectGraceInRoom(room.id())).isFalse();
		assertThat(roomRegistry.hasRoom(room.id())).isFalse();
		sessionRegistry.shutdown();
	}

	private static GameRoomProperties properties(Duration reconnectGracePeriod) {
		return new GameRoomProperties(
				"main",
				5,
				100,
				Duration.ofMillis(100),
				reconnectGracePeriod
		);
	}
}
