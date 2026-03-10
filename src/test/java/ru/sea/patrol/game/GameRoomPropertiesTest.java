package ru.sea.patrol.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.sea.patrol.SeaPatrolApplication;
import ru.sea.patrol.service.game.GameService;

@SpringBootTest(classes = SeaPatrolApplication.class)
class GameRoomPropertiesTest {

	@Autowired
	private GameService gameService;

	@Test
	void bindsMvpDefaultsFromApplicationYaml() {
		assertThat(gameService.getDefaultRoomName()).isEqualTo("main");
		assertThat(gameService.getMaxRooms()).isEqualTo(5);
		assertThat(gameService.getMaxPlayersPerRoom()).isEqualTo(100);
		assertThat(gameService.getRoomUpdatePeriodMillis()).isEqualTo(100L);
		assertThat(gameService.getReconnectGracePeriod()).isEqualTo(Duration.ofSeconds(15));
		assertThat(gameService.getEmptyRoomIdleTimeout()).isEqualTo(Duration.ofSeconds(30));
	}
}

@SpringBootTest(
		classes = SeaPatrolApplication.class,
		properties = {
				"game.room.default-room-name=sandbox-dev",
				"game.room.max-rooms=9",
				"game.room.max-players-per-room=24",
				"game.room.update-period=250ms",
				"game.room.reconnect-grace-period=45s",
				"game.room.empty-room-idle-timeout=60s"
		}
)
class GameRoomPropertiesOverrideTest {

	@Autowired
	private GameService gameService;

	@Test
	void allowsOverridingRoomConfiguration() {
		assertThat(gameService.getDefaultRoomName()).isEqualTo("sandbox-dev");
		assertThat(gameService.getMaxRooms()).isEqualTo(9);
		assertThat(gameService.getMaxPlayersPerRoom()).isEqualTo(24);
		assertThat(gameService.getRoomUpdatePeriodMillis()).isEqualTo(250L);
		assertThat(gameService.getReconnectGracePeriod()).isEqualTo(Duration.ofSeconds(45));
		assertThat(gameService.getEmptyRoomIdleTimeout()).isEqualTo(Duration.ofSeconds(60));
	}
}
