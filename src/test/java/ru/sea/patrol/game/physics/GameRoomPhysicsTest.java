package ru.sea.patrol.game.physics;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.physics.box2d.Box2D;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import ru.sea.patrol.MessageType;
import ru.sea.patrol.dto.websocket.InitGameStateMessage;
import ru.sea.patrol.dto.websocket.MessageOutput;
import ru.sea.patrol.service.game.GameRoom;
import ru.sea.patrol.service.game.Player;

@Tag("physics")
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GameRoomPhysicsTest {

	@BeforeAll
	void initBox2D() {
		Box2D.init();
	}

	@Test
	void start_withoutScheduler_createsWorld_andEmitsInitMessage() throws Exception {
		GameRoom room = new GameRoom("test-room");
		Player player = createPlayer("p1");
		room.join(player);

		CompletableFuture<MessageOutput> firstFuture = room.getSink().asFlux().next().toFuture();

		room.start(false);
		try {
			MessageOutput first = firstFuture.get(2, TimeUnit.SECONDS);

			assertThat(room.isStarted()).isTrue();
			assertThat(room.getWorld()).isNotNull();
			assertThat(player.getShip()).isNotNull();

			assertThat(first).isNotNull();
			assertThat(first.getType()).isEqualTo(MessageType.INIT_GAME_STATE);
			assertThat(first.getPayload()).isInstanceOf(InitGameStateMessage.class);

			InitGameStateMessage payload = (InitGameStateMessage) first.getPayload();
			assertThat(payload.room()).isEqualTo("test-room");
			assertThat(payload.players()).hasSize(1);
			assertThat(payload.players().get(0).name()).isEqualTo("p1");
		} finally {
			room.stop();
		}
	}

	@Test
	void update_withoutScheduler_doesNotThrow() {
		GameRoom room = new GameRoom("test-room");
		room.join(createPlayer("p1"));
		room.start(false);

		try {
			for (int i = 0; i < 20; i++) {
				room.update();
			}
		} finally {
			room.stop();
		}

		assertThat(room.isStarted()).isFalse();
		assertThat(room.getWorld()).isNull();
	}

	@Test
	void startStop_multipleCycles_staysStable() {
		for (int i = 0; i < 3; i++) {
			GameRoom room = new GameRoom("room-" + i);
			room.join(createPlayer("p" + i));
			room.start(false);
			room.update();
			room.stop();

			assertThat(room.isStarted()).isFalse();
			assertThat(room.getWorld()).isNull();
		}
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
