package ru.sea.patrol.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import reactor.core.Disposable;
import ru.sea.patrol.service.game.GameRoom;
import ru.sea.patrol.service.game.GameRoomProperties;
import ru.sea.patrol.service.game.GameService;
import ru.sea.patrol.service.game.RoomCatalogService;
import ru.sea.patrol.service.game.RoomCatalogWsService;
import ru.sea.patrol.service.game.RoomRegistry;
import ru.sea.patrol.service.game.RoomRegistryEntry;
import ru.sea.patrol.service.game.SpawnService;
import ru.sea.patrol.service.game.map.MapTemplateRegistry;
import ru.sea.patrol.service.session.GameSessionRegistry;
import ru.sea.patrol.ws.protocol.MessageType;
import ru.sea.patrol.ws.protocol.dto.MessageOutput;
import ru.sea.patrol.ws.protocol.dto.SpawnAssignedResponseDto;
import ru.sea.patrol.ws.protocol.dto.SpawnReason;
import tools.jackson.databind.ObjectMapper;

class GameServiceSpawnAssignedTest {

	@Test
	void respawnPlayer_emitsRespawnAssigned_forActiveRoomPlayer() {
		GameRoomProperties properties = new GameRoomProperties(
				"main",
				5,
				100,
				Duration.ofMillis(100),
				Duration.ofSeconds(15),
				Duration.ofSeconds(30)
		);
		RoomRegistry roomRegistry = new RoomRegistry(properties);
		RoomCatalogService roomCatalogService = new RoomCatalogService(roomRegistry, properties, newMapTemplateRegistry());
		RoomCatalogWsService roomCatalogWsService = new RoomCatalogWsService(roomCatalogService);
		ApplicationEventPublisher eventPublisher = event -> {
		};
		GameSessionRegistry sessionRegistry = new GameSessionRegistry(properties, roomRegistry, eventPublisher);
		GameService gameService = new GameService(new ObjectMapper(), properties, roomRegistry, sessionRegistry, new SpawnService());
		RoomRegistryEntry room = roomRegistry.createRoom("Sandbox 1", "caribbean-01", "Caribbean Sea");
		List<MessageOutput> messages = new CopyOnWriteArrayList<>();
		Disposable subscription = gameService.initialize("alice").subscribe(messages::add);

		try {
			gameService.prepareRoomJoin("alice", room.id());
			gameService.emitInitialSpawnAssigned("alice", room.id());
			gameService.activateRoomJoin("alice", room.id());
			messages.clear();

			SpawnAssignedResponseDto respawn = gameService.respawnPlayer("alice");

			assertThat(respawn.roomId()).isEqualTo(room.id());
			assertThat(respawn.reason()).isEqualTo(SpawnReason.RESPAWN);
			assertThat(respawn.x()).isBetween(-30.0, 30.0);
			assertThat(respawn.z()).isBetween(-30.0, 30.0);
			assertThat(respawn.angle()).isEqualTo(0.0);

			MessageOutput respawnMessage = messages.stream()
					.filter(message -> message.getType() == MessageType.SPAWN_ASSIGNED)
					.findFirst()
					.orElseThrow();
			SpawnAssignedResponseDto payload = (SpawnAssignedResponseDto) respawnMessage.getPayload();
			assertThat(payload.reason()).isEqualTo(SpawnReason.RESPAWN);
			assertThat(payload.roomId()).isEqualTo(room.id());
			assertThat(payload.x()).isEqualTo(respawn.x());
			assertThat(payload.z()).isEqualTo(respawn.z());
			assertThat(payload.angle()).isEqualTo(respawn.angle());
		} finally {
			subscription.dispose();
			GameRoom activeRoom = roomRegistry.findRoom(room.id());
			if (activeRoom != null) {
				activeRoom.stop();
			}
			sessionRegistry.shutdown();
			roomRegistry.shutdown();
		}
	}

	private static MapTemplateRegistry newMapTemplateRegistry() {
		return new MapTemplateRegistry(
				new ObjectMapper(),
				new PathMatchingResourcePatternResolver(),
				"classpath*:worlds/*/manifest.json"
		);
	}
}