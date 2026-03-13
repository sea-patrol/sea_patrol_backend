package ru.sea.patrol.service.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import ru.sea.patrol.service.game.GameRoomProperties;
import ru.sea.patrol.service.game.RoomRegistry;

class GameSessionRegistryTest {

	private static GameRoomProperties newProperties(Duration reconnectGracePeriod, Duration emptyRoomIdleTimeout) {
		return new GameRoomProperties(
				"main",
				5,
				100,
				Duration.ofMillis(100),
				reconnectGracePeriod,
				emptyRoomIdleTimeout,
				0.17453292
		);
	}

	private static GameSessionRegistry newRegistry(GameRoomProperties properties, RoomRegistry roomRegistry) {
		ApplicationEventPublisher eventPublisher = event -> {
		};
		return new GameSessionRegistry(properties, roomRegistry, eventPublisher);
	}

	@Test
	void claimSession_rejectsParallelActiveSession() {
		GameRoomProperties properties = newProperties(Duration.ofSeconds(15), Duration.ofSeconds(30));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);

		try {
			assertThat(registry.claimSession("alice", "s1")).isEqualTo(GameSessionRegistry.ClaimResult.NEW_SESSION);
			assertThat(registry.isLoginAllowed("alice")).isFalse();
			assertThat(registry.claimSession("alice", "s2")).isEqualTo(GameSessionRegistry.ClaimResult.REJECTED_DUPLICATE);
		} finally {
			registry.shutdown();
			roomRegistry.shutdown();
		}
	}

	@Test
	void disconnectImmediately_releasesLoginAdmission_andStartsGraceWindow() {
		GameRoomProperties properties = newProperties(Duration.ofSeconds(15), Duration.ofSeconds(30));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);
		try {
			registry.claimSession("alice", "s1");

			assertThat(registry.registerDisconnect("alice", "s1")).isTrue();
			assertThat(registry.isLoginAllowed("alice")).isTrue();
			assertThat(registry.isInReconnectGrace("alice")).isTrue();
			assertThat(registry.hasActiveLobbySession("alice")).isFalse();
		} finally {
			registry.shutdown();
			roomRegistry.shutdown();
		}
	}

	@Test
	void claimSession_allowsReconnectWithinGraceWindow() {
		GameRoomProperties properties = newProperties(Duration.ofSeconds(15), Duration.ofSeconds(30));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);
		try {
			registry.claimSession("alice", "s1");
			registry.registerDisconnect("alice", "s1");

			assertThat(registry.isInReconnectGrace("alice")).isTrue();
			assertThat(registry.isLoginAllowed("alice")).isTrue();
			assertThat(registry.claimSession("alice", "s2")).isEqualTo(GameSessionRegistry.ClaimResult.RECONNECTED_SESSION);
			assertThat(registry.isInReconnectGrace("alice")).isFalse();
		} finally {
			registry.shutdown();
			roomRegistry.shutdown();
		}
	}

	@Test
	void staleDisconnect_doesNotDropNewerActiveSession() {
		GameRoomProperties properties = newProperties(Duration.ofSeconds(15), Duration.ofSeconds(30));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);
		try {
			registry.claimSession("alice", "s1");
			registry.registerDisconnect("alice", "s1");
			assertThat(registry.claimSession("alice", "s2")).isEqualTo(GameSessionRegistry.ClaimResult.RECONNECTED_SESSION);

			assertThat(registry.registerDisconnect("alice", "s1")).isFalse();
			assertThat(registry.isLoginAllowed("alice")).isFalse();
			assertThat(registry.hasActiveLobbySession("alice")).isTrue();
		} finally {
			registry.shutdown();
			roomRegistry.shutdown();
		}
	}

	@Test
	void claimSession_startsInLobby_andCanBindToRoomOnce() {
		GameRoomProperties properties = newProperties(Duration.ofSeconds(15), Duration.ofSeconds(30));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);
		try {
			registry.claimSession("alice", "s1");

			assertThat(registry.hasActiveLobbySession("alice")).isTrue();
			assertThat(registry.activeRoomId("alice")).isNull();
			assertThat(registry.bindToRoom("alice", "sandbox-1")).isTrue();
			assertThat(registry.hasActiveLobbySession("alice")).isFalse();
			assertThat(registry.activeRoomId("alice")).isEqualTo("sandbox-1");
			assertThat(registry.bindToRoom("alice", "sandbox-2")).isFalse();
		} finally {
			registry.shutdown();
			roomRegistry.shutdown();
		}
	}

	@Test
	void reconnectFromGrace_restoresRoomBinding() {
		GameRoomProperties properties = newProperties(Duration.ofSeconds(15), Duration.ofSeconds(30));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);
		try {
			registry.claimSession("alice", "s1");
			assertThat(registry.bindToRoom("alice", "sandbox-1")).isTrue();
			assertThat(registry.registerDisconnect("alice", "s1")).isTrue();

			assertThat(registry.claimSession("alice", "s2")).isEqualTo(GameSessionRegistry.ClaimResult.RECONNECTED_SESSION);
			assertThat(registry.hasActiveLobbySession("alice")).isFalse();
			assertThat(registry.activeRoomId("alice")).isEqualTo("sandbox-1");
		} finally {
			registry.shutdown();
			roomRegistry.shutdown();
		}
	}

	@Test
	void bindToLobbyFromRoom_rebindsActiveRoomSessionBackToLobby() {
		GameRoomProperties properties = newProperties(Duration.ofSeconds(15), Duration.ofSeconds(30));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);
		try {
			registry.claimSession("alice", "s1");
			assertThat(registry.bindToRoom("alice", "sandbox-1")).isTrue();

			assertThat(registry.bindToLobbyFromRoom("alice", "sandbox-1"))
					.isEqualTo(GameSessionRegistry.LobbyRebindResult.SUCCESS);
			assertThat(registry.hasActiveLobbySession("alice")).isTrue();
			assertThat(registry.activeRoomId("alice")).isNull();
		} finally {
			registry.shutdown();
			roomRegistry.shutdown();
		}
	}

	@Test
	void bindToLobbyFromRoom_rejectsLobbyOrDifferentRoomBinding() {
		GameRoomProperties properties = newProperties(Duration.ofSeconds(15), Duration.ofSeconds(30));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);
		try {
			registry.claimSession("alice", "s1");

			assertThat(registry.bindToLobbyFromRoom("alice", "sandbox-1"))
					.isEqualTo(GameSessionRegistry.LobbyRebindResult.ROOM_SESSION_REQUIRED);

			assertThat(registry.bindToRoom("alice", "sandbox-1")).isTrue();
			assertThat(registry.bindToLobbyFromRoom("alice", "sandbox-2"))
					.isEqualTo(GameSessionRegistry.LobbyRebindResult.ROOM_SESSION_MISMATCH);
			assertThat(registry.activeRoomId("alice")).isEqualTo("sandbox-1");
		} finally {
			registry.shutdown();
			roomRegistry.shutdown();
		}
	}

	@Test
	void reconnectFromGrace_keepsRetainedEmptyRoomUntilIdleTimeout() throws Exception {
		GameRoomProperties properties = newProperties(Duration.ofSeconds(15), Duration.ofMillis(120));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		roomRegistry.createRoom("Sandbox 1", "caribbean-01", "Caribbean Sea");
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);
		try {
			registry.claimSession("alice", "s1");
			registry.bindToRoom("alice", "sandbox-1");
			registry.registerDisconnect("alice", "s1");

			assertThat(roomRegistry.hasRoom("sandbox-1")).isTrue();
			assertThat(registry.claimSession("alice", "s2")).isEqualTo(GameSessionRegistry.ClaimResult.RECONNECTED_SESSION);
			assertThat(roomRegistry.hasRoom("sandbox-1")).isTrue();

			Thread.sleep(220L);

			assertThat(roomRegistry.hasRoom("sandbox-1")).isFalse();
		} finally {
			registry.shutdown();
			roomRegistry.shutdown();
		}
	}

	@Test
	void disconnectGrace_expiresAndDropsTrackedSession() throws Exception {
		GameRoomProperties properties = newProperties(Duration.ofMillis(120), Duration.ofSeconds(30));
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		GameSessionRegistry registry = newRegistry(properties, roomRegistry);
		try {
			registry.claimSession("alice", "s1");
			registry.registerDisconnect("alice", "s1");

			Thread.sleep(250L);

			assertThat(registry.hasTrackedSession("alice")).isFalse();
			assertThat(registry.claimSession("alice", "s2")).isEqualTo(GameSessionRegistry.ClaimResult.NEW_SESSION);
		} finally {
			registry.shutdown();
			roomRegistry.shutdown();
		}
	}
}
