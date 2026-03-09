package ru.sea.patrol.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import ru.sea.patrol.service.game.GameRoom;
import ru.sea.patrol.service.game.GameRoomProperties;
import ru.sea.patrol.service.game.Player;
import ru.sea.patrol.service.game.RoomRegistry;

class RoomRegistryTest {

	@Test
	void getOrCreateRoom_returnsExistingRoom_andTracksSingleRegistryEntry() {
		RoomRegistry registry = new RoomRegistry(defaultProperties());
		try {
			GameRoom first = registry.getOrCreateRoom("sandbox-1");
			GameRoom second = registry.getOrCreateRoom("sandbox-1");

			assertThat(first).isSameAs(second);
			assertThat(registry.roomCount()).isEqualTo(1);
			assertThat(registry.hasRoom("sandbox-1")).isTrue();
		} finally {
			registry.shutdown();
		}
	}

	@Test
	void createRoom_removesEmptyRegistryEntry_afterIdleTimeout() throws Exception {
		RoomRegistry registry = new RoomRegistry(shortTimeoutProperties());
		try {
			registry.createRoom("Sandbox 1", "caribbean-01", "Caribbean Sea");

			assertThat(registry.hasRoom("sandbox-1")).isTrue();
			Thread.sleep(180L);

			assertThat(registry.hasRoom("sandbox-1")).isFalse();
			assertThat(registry.roomCount()).isZero();
		} finally {
			registry.shutdown();
		}
	}

	@Test
	void scheduledCleanup_isCanceled_whenPlayerJoinsBeforeTimeout() throws Exception {
		RoomRegistry registry = new RoomRegistry(shortTimeoutProperties());
		try {
			GameRoom room = registry.getOrCreateRoom("sandbox-1");
			room.join(createPlayer("alice"));
			registry.cancelEmptyRoomCleanup("sandbox-1");

			Thread.sleep(180L);

			assertThat(registry.hasRoom("sandbox-1")).isTrue();
			assertThat(registry.roomCount()).isEqualTo(1);
		} finally {
			registry.shutdown();
		}
	}

	private static GameRoomProperties defaultProperties() {
		return new GameRoomProperties(
				"main",
				5,
				100,
				Duration.ofMillis(100),
				Duration.ofSeconds(15),
				Duration.ofSeconds(30)
		);
	}

	private static GameRoomProperties shortTimeoutProperties() {
		return new GameRoomProperties(
				"main",
				5,
				100,
				Duration.ofMillis(100),
				Duration.ofSeconds(15),
				Duration.ofMillis(100)
		);
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
