package ru.sea.patrol.service.game;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.sea.patrol.service.session.GameSessionRegistry;
import ru.sea.patrol.ws.protocol.MessageType;
import ru.sea.patrol.ws.protocol.dto.MessageInput;
import ru.sea.patrol.ws.protocol.dto.MessageOutput;
import ru.sea.patrol.ws.protocol.dto.PlayerInputMessage;
import ru.sea.patrol.ws.protocol.dto.SpawnAssignedResponseDto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

	private final ObjectMapper objectMapper;
	private final GameRoomProperties roomProperties;
	private final RoomRegistry roomRegistry;
	private final GameSessionRegistry sessionRegistry;
	private final SpawnService spawnService;

	private final Map<String, Player> players = new ConcurrentHashMap<>();

	public Flux<MessageOutput> initialize(String playerName) {
		var player = retrievePlayer(playerName);
		return player.getSink().asFlux();
	}

	public Mono<Void> handle(String username, MessageInput msg) {
		switch (msg.getType()) {
			case MessageType.PLAYER_INPUT:
				return handlePlayerInput(username, msg.getPayload());
			default:
				return Mono.empty();
		}
	}

	public String getDefaultRoomName() {
		return roomProperties.defaultRoomName();
	}

	public int getMaxRooms() {
		return roomProperties.maxRooms();
	}

	public int getMaxPlayersPerRoom() {
		return roomProperties.maxPlayersPerRoom();
	}

	public Duration getReconnectGracePeriod() {
		return roomProperties.reconnectGracePeriod();
	}

	public long getRoomUpdatePeriodMillis() {
		return roomProperties.updatePeriod().toMillis();
	}

	public void prepareRoomJoin(String playerName, String roomId) {
		var room = roomRegistry.findRoom(roomId);
		if (room == null) {
			throw new IllegalArgumentException("Room not found: " + roomId);
		}
		room.join(retrievePlayer(playerName), false);
	}

	public SpawnAssignedResponseDto assignInitialSpawn(String playerName, String roomId) {
		var player = retrievePlayer(playerName);
		SpawnPoint spawnPoint = spawnService.calculateInitialSpawn();
		player.setX((float) spawnPoint.x())
				.setZ((float) spawnPoint.z())
				.setAngle((float) spawnPoint.angle());
		if (player.getShip() != null) {
			player.getShip().setFrontendTransform((float) spawnPoint.x(), (float) spawnPoint.z(), (float) spawnPoint.angle());
		}
		return new SpawnAssignedResponseDto(roomId, "INITIAL", spawnPoint.x(), spawnPoint.z(), spawnPoint.angle());
	}

	public void activateRoomJoin(String playerName, String roomId) {
		var room = roomRegistry.findRoom(roomId);
		var player = players.get(playerName);
		if (room == null || player == null) {
			return;
		}
		if (room.isStarted()) {
			room.activateJoinedPlayer(player);
			return;
		}
		player.activateRoomSubscription();
		room.start();
	}

	public void replyToPlayer(String playerName, MessageOutput message) {
		var player = retrievePlayer(playerName);
		player.reply(message);
	}

	public boolean leaveRoom(String playerName, String roomName) {
		var room = roomRegistry.findRoom(roomName);
		if (room == null) {
			return false;
		}
		int beforeCount = room.getPlayerCount();
		room.leave(playerName);
		if (!sessionRegistry.hasReconnectGraceInRoom(roomName)) {
			roomRegistry.removeRoomIfEmpty(roomName);
		}
		return room.getPlayerCount() != beforeCount || !roomRegistry.hasRoom(roomName);
	}

	public boolean cleanupPlayer(String playerName) {
		log.info("Cleaning up player {}", playerName);
		var player = players.remove(playerName);
		if (player != null && player.getRoom() != null) {
			return leaveRoom(playerName, player.getRoom().getName());
		}
		return false;
	}

	private Mono<Void> handlePlayerInput(String username, JsonNode payload) {
		try {
			PlayerInputMessage msg = objectMapper.treeToValue(payload, PlayerInputMessage.class);
			var player = players.get(username);
			if (player != null && player.getShip() != null) {
				log.info("Player {} input: {}", username, msg);
				player.getShip().setInput(msg);
			}
			return Mono.empty();
		} catch (Exception e) {
			return Mono.error(e);
		}
	}

	private Player retrievePlayer(String playerName) {
		return players.computeIfAbsent(playerName, name -> new Player(name)
				.setModel("model")
				.setMaxHealth(500)
				.setHealth(500)
				.setAngle(0)
				.setVelocity(0)
				.setX(0)
				.setZ(0)
				.setHeight(4f)
				.setWidth(7f)
				.setLength(26f));
	}
}
