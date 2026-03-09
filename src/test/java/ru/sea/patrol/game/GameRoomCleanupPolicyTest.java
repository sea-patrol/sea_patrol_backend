package ru.sea.patrol.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import ru.sea.patrol.service.game.GameRoomProperties;
import ru.sea.patrol.service.game.GameService;
import ru.sea.patrol.service.game.RoomCatalogService;
import ru.sea.patrol.service.game.RoomCatalogWsService;
import ru.sea.patrol.service.game.RoomRegistry;
import ru.sea.patrol.service.game.RoomRegistryEntry;
import ru.sea.patrol.service.session.GameSessionRegistry;
import ru.sea.patrol.service.session.SessionGraceExpiredEvent;
import tools.jackson.databind.ObjectMapper;

class GameRoomCleanupPolicyTest {

	@Test
	void cleanupPlayer_removesEmptyRoomImmediately_withoutReconnectGrace() {
		GameRoomProperties properties = properties(Duration.ofSeconds(15));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		RoomCatalogService roomCatalogService = new RoomCatalogService(roomRegistry, properties);
		RoomCatalogWsService roomCatalogWsService = new RoomCatalogWsService(roomCatalogService);
		ApplicationEventPublisher eventPublisher = event -> {
		};
		GameSessionRegistry sessionRegistry = new GameSessionRegistry(properties, roomRegistry, roomCatalogWsService, eventPublisher);
		GameService gameService = new GameService(new ObjectMapper(), properties, roomRegistry, sessionRegistry, new ru.sea.patrol.service.game.SpawnService());
		RoomRegistryEntry room = roomRegistry.createRoom("Sandbox 1", "caribbean-01", "Caribbean Sea");

		gameService.prepareRoomJoin("alice", room.id());
		gameService.cleanupPlayer("alice");

		assertThat(roomRegistry.hasRoom(room.id())).isFalse();
		sessionRegistry.shutdown();
	}

	@Test
	void disconnectPlayer_keepsRoomStateWhileReconnectGraceExists_andRemovesAfterExpiration() throws Exception {
		GameRoomProperties properties = properties(Duration.ofMillis(120));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		RoomCatalogService roomCatalogService = new RoomCatalogService(roomRegistry, properties);
		RoomCatalogWsService roomCatalogWsService = new RoomCatalogWsService(roomCatalogService);
		AtomicReference<GameService> gameServiceRef = new AtomicReference<>();
		ApplicationEventPublisher eventPublisher = event -> {
			if (event instanceof SessionGraceExpiredEvent expiredEvent) {
				gameServiceRef.get().cleanupPlayer(expiredEvent.username());
			}
		};
		GameSessionRegistry sessionRegistry = new GameSessionRegistry(properties, roomRegistry, roomCatalogWsService, eventPublisher);
		GameService gameService = new GameService(new ObjectMapper(), properties, roomRegistry, sessionRegistry, new ru.sea.patrol.service.game.SpawnService());
		gameServiceRef.set(gameService);
		RoomRegistryEntry room = roomRegistry.createRoom("Sandbox 2", "caribbean-01", "Caribbean Sea");

		sessionRegistry.claimSession("alice", "s1");
		gameService.prepareRoomJoin("alice", room.id());
		assertThat(sessionRegistry.bindToRoom("alice", room.id())).isTrue();

		sessionRegistry.registerDisconnect("alice", "s1");
		gameService.disconnectPlayer("alice");

		assertThat(sessionRegistry.hasReconnectGraceInRoom(room.id())).isTrue();
		assertThat(roomRegistry.hasRoom(room.id())).isTrue();
		assertThat(room.room().getPlayerCount()).isEqualTo(1);

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
