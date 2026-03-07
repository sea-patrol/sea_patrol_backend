package ru.sea.patrol.service.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import ru.sea.patrol.service.game.GameRoomProperties;
import ru.sea.patrol.service.game.RoomCatalogService;
import ru.sea.patrol.service.game.RoomCatalogWsService;
import ru.sea.patrol.service.game.RoomRegistry;

class GameSessionRegistryTest {

	private static GameRoomProperties newProperties(Duration reconnectGracePeriod) {
		return new GameRoomProperties(
				"main",
				5,
				100,
				Duration.ofMillis(100),
				reconnectGracePeriod
		);
	}

	private static GameSessionRegistry newRegistry(GameRoomProperties properties, RoomRegistry roomRegistry) {
		RoomCatalogService roomCatalogService = new RoomCatalogService(roomRegistry, properties);
		RoomCatalogWsService roomCatalogWsService = new RoomCatalogWsService(roomCatalogService);
		return new GameSessionRegistry(properties, roomRegistry, roomCatalogWsService);
	}

	@Test
	void claimSession_rejectsParallelActiveSession() {
		GameRoomProperties properties = newProperties(Duration.ofSeconds(30));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);

		assertThat(registry.claimSession("alice", "s1")).isEqualTo(GameSessionRegistry.ClaimResult.NEW_SESSION);
		assertThat(registry.isLoginAllowed("alice")).isFalse();
		assertThat(registry.claimSession("alice", "s2")).isEqualTo(GameSessionRegistry.ClaimResult.REJECTED_DUPLICATE);
		registry.shutdown();
	}

	@Test
	void claimSession_allowsReconnectWithinGraceWindow() {
		GameRoomProperties properties = newProperties(Duration.ofSeconds(30));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);
		registry.claimSession("alice", "s1");
		registry.registerDisconnect("alice", "s1");

		assertThat(registry.isInReconnectGrace("alice")).isTrue();
		assertThat(registry.isLoginAllowed("alice")).isTrue();
		assertThat(registry.claimSession("alice", "s2")).isEqualTo(GameSessionRegistry.ClaimResult.RECONNECTED_SESSION);
		assertThat(registry.isInReconnectGrace("alice")).isFalse();
		registry.shutdown();
	}

	@Test
	void claimSession_startsInLobby_andCanBindToRoomOnce() {
		GameRoomProperties properties = newProperties(Duration.ofSeconds(30));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);
		registry.claimSession("alice", "s1");

		assertThat(registry.hasActiveLobbySession("alice")).isTrue();
		assertThat(registry.bindToRoom("alice", "sandbox-1")).isTrue();
		assertThat(registry.hasActiveLobbySession("alice")).isFalse();
		assertThat(registry.bindToRoom("alice", "sandbox-2")).isFalse();
		registry.shutdown();
	}

	@Test
	void reconnectFromGrace_releasesRetainedEmptyRoom() {
		GameRoomProperties properties = newProperties(Duration.ofSeconds(30));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		roomRegistry.createRoom("Sandbox 1", "caribbean-01", "Caribbean Sea");
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);
		registry.claimSession("alice", "s1");
		registry.bindToRoom("alice", "sandbox-1");
		registry.registerDisconnect("alice", "s1");

		assertThat(roomRegistry.hasRoom("sandbox-1")).isTrue();
		assertThat(registry.claimSession("alice", "s2")).isEqualTo(GameSessionRegistry.ClaimResult.RECONNECTED_SESSION);
		assertThat(roomRegistry.hasRoom("sandbox-1")).isFalse();
		registry.shutdown();
	}

	@Test
	void disconnectGrace_expiresAndDropsTrackedSession() throws Exception {
		GameRoomProperties properties = newProperties(Duration.ofMillis(120));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);
		registry.claimSession("alice", "s1");
		registry.registerDisconnect("alice", "s1");

		Thread.sleep(250L);

		assertThat(registry.hasTrackedSession("alice")).isFalse();
		assertThat(registry.claimSession("alice", "s2")).isEqualTo(GameSessionRegistry.ClaimResult.NEW_SESSION);
		registry.shutdown();
	}
}
