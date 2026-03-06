package ru.sea.patrol.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.sea.patrol.service.game.GameRoom;
import ru.sea.patrol.service.game.Player;

class GameRoomLifecycleTest {

	@Test
	void leave_lastPlayer_stopsRoom() {
		GameRoom room = new GameRoom("test-room", 100L);
		Player player = new Player("alice")
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

		room.join(player);
		room.start(false);

		assertThat(room.isStarted()).isTrue();
		assertThat(room.getWorld()).isNotNull();

		room.leave("alice");

		assertThat(room.isStarted()).isFalse();
		assertThat(room.getWorld()).isNull();
	}
}
