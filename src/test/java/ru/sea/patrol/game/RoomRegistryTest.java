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

		GameRoom first = registry.getOrCreateRoom("sandbox-1");
		GameRoom second = registry.getOrCreateRoom("sandbox-1");

		assertThat(first).isSameAs(second);
		assertThat(registry.roomCount()).isEqualTo(1);
		assertThat(registry.hasRoom("sandbox-1")).isTrue();
	}

	@Test
	void removeRoomIfEmpty_removesRegistryEntry_afterLastPlayerLeaves() {
		RoomRegistry registry = new RoomRegistry(defaultProperties());
		GameRoom room = registry.getOrCreateRoom("sandbox-1");
		room.join(createPlayer("alice"));
		room.start(false);

		room.leave("alice");
		boolean removed = registry.removeRoomIfEmpty("sandbox-1");

		assertThat(removed).isTrue();
		assertThat(registry.hasRoom("sandbox-1")).isFalse();
		assertThat(registry.roomCount()).isZero();
	}

	private static GameRoomProperties defaultProperties() {
		return new GameRoomProperties(
				"main",
				5,
				100,
				Duration.ofMillis(100),
				Duration.ofSeconds(30)
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
