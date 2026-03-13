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
import ru.sea.patrol.service.game.GameRoom;
import ru.sea.patrol.service.game.Player;
import ru.sea.patrol.service.game.map.MapTemplate;
import ru.sea.patrol.ws.protocol.MessageType;
import ru.sea.patrol.ws.protocol.dto.InitGameStateMessage;
import ru.sea.patrol.ws.protocol.dto.MessageOutput;
import ru.sea.patrol.ws.protocol.dto.UpdateGameStateMessage;

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
		GameRoom room = new GameRoom("test-room", 100L);
		Player player = createPlayer("p1");
		room.join(player);

		CompletableFuture<MessageOutput> firstFuture = player.getSink().asFlux().next().toFuture();

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
			assertThat(payload.wind()).isNotNull();
			assertThat(payload.wind().angle()).isEqualTo(0.0f);
			assertThat(payload.wind().speed()).isEqualTo(10.0f);
			assertThat(payload.players()).hasSize(1);
			assertThat(payload.players().get(0).name()).isEqualTo("p1");
		} finally {
			room.stop();
		}
	}

	@Test
	void update_withoutScheduler_doesNotThrow() {
		GameRoom room = new GameRoom("test-room", 100L);
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
	void update_withoutScheduler_emitsSameWindSnapshotForAllPlayers() throws Exception {
		GameRoom room = new GameRoom("wind-room", "Wind Room", MapTemplate.mvpDefault(), 100L);
		Player firstPlayer = createPlayer("p1");
		Player secondPlayer = createPlayer("p2");
		room.join(firstPlayer);
		room.join(secondPlayer);

		CompletableFuture<MessageOutput> firstUpdateFuture = firstPlayer.getSink().asFlux().skip(1).next().toFuture();
		CompletableFuture<MessageOutput> secondUpdateFuture = secondPlayer.getSink().asFlux().skip(1).next().toFuture();

		room.start(false);
		try {
			room.update();

			MessageOutput firstUpdate = firstUpdateFuture.get(2, TimeUnit.SECONDS);
			MessageOutput secondUpdate = secondUpdateFuture.get(2, TimeUnit.SECONDS);

			assertThat(firstUpdate.getType()).isEqualTo(MessageType.UPDATE_GAME_STATE);
			assertThat(secondUpdate.getType()).isEqualTo(MessageType.UPDATE_GAME_STATE);
			assertThat(firstUpdate.getPayload()).isInstanceOf(UpdateGameStateMessage.class);
			assertThat(secondUpdate.getPayload()).isInstanceOf(UpdateGameStateMessage.class);

			UpdateGameStateMessage firstPayload = (UpdateGameStateMessage) firstUpdate.getPayload();
			UpdateGameStateMessage secondPayload = (UpdateGameStateMessage) secondUpdate.getPayload();

			assertThat(firstPayload.wind()).isNotNull();
			assertThat(secondPayload.wind()).isNotNull();
			assertThat(firstPayload.wind().angle()).isEqualTo(secondPayload.wind().angle());
			assertThat(firstPayload.wind().speed()).isEqualTo(secondPayload.wind().speed());
			assertThat(firstPayload.wind().speed()).isGreaterThan(0.0f);
			assertThat(firstPayload.players()).hasSize(2);
			assertThat(secondPayload.players()).hasSize(2);
		} finally {
			room.stop();
		}
	}

	@Test
	void update_withoutScheduler_rotatesWindClockwise() throws Exception {
		MapTemplate defaultMap = MapTemplate.mvpDefault();
		MapTemplate clockwiseMap = new MapTemplate(
				"clockwise-room",
				"Clockwise Room",
				defaultMap.region(),
				defaultMap.revision(),
				false,
				true,
				defaultMap.bounds(),
				defaultMap.spawnRules(),
				defaultMap.files(),
				defaultMap.presentation(),
				new MapTemplate.WindSettings(Math.PI / 2, 10.0),
				defaultMap.colliders(),
				defaultMap.spawnPoints(),
				defaultMap.pointsOfInterest(),
				defaultMap.minimap()
		);
		GameRoom room = new GameRoom("wind-room", "Wind Room", clockwiseMap, 100L, Math.PI / 2);
		Player player = createPlayer("p1");
		room.join(player);

		CompletableFuture<MessageOutput> initFuture = player.getSink().asFlux().next().toFuture();
		CompletableFuture<MessageOutput> updateFuture = player.getSink().asFlux().skip(1).next().toFuture();

		room.start(false);
		try {
			MessageOutput initMessage = initFuture.get(2, TimeUnit.SECONDS);
			Thread.sleep(150L);
			room.update();
			MessageOutput updateMessage = updateFuture.get(2, TimeUnit.SECONDS);

			InitGameStateMessage initPayload = (InitGameStateMessage) initMessage.getPayload();
			UpdateGameStateMessage updatePayload = (UpdateGameStateMessage) updateMessage.getPayload();

			assertThat(updatePayload.wind().angle()).isLessThan(initPayload.wind().angle());
			assertThat(updatePayload.wind().speed()).isEqualTo(initPayload.wind().speed());
		} finally {
			room.stop();
		}
	}

	@Test
	void startStop_multipleCycles_staysStable() {
		for (int i = 0; i < 3; i++) {
			GameRoom room = new GameRoom("room-" + i, 100L);
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
