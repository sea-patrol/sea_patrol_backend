package ru.sea.patrol.service.game;

import org.springframework.stereotype.Service;
import ru.sea.patrol.error.domain.ConflictException;
import ru.sea.patrol.error.domain.NotFoundException;
import ru.sea.patrol.service.chat.ChatService;
import ru.sea.patrol.service.session.GameSessionRegistry;
import ru.sea.patrol.ws.protocol.MessageType;
import ru.sea.patrol.ws.protocol.dto.MessageOutput;
import ru.sea.patrol.ws.protocol.dto.RoomJoinResponseDto;
import ru.sea.patrol.ws.protocol.dto.SpawnAssignedResponseDto;

@Service
public class RoomJoinService {

	private static final String ERROR_CODE_ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
	private static final String ERROR_CODE_ROOM_FULL = "ROOM_FULL";
	private static final String ERROR_CODE_LOBBY_SESSION_REQUIRED = "LOBBY_SESSION_REQUIRED";

	private final RoomRegistry roomRegistry;
	private final GameRoomProperties roomProperties;
	private final GameSessionRegistry sessionRegistry;
	private final GameService gameService;
	private final ChatService chatService;
	private final RoomCatalogWsService roomCatalogWsService;

	public RoomJoinService(
			RoomRegistry roomRegistry,
			GameRoomProperties roomProperties,
			GameSessionRegistry sessionRegistry,
			GameService gameService,
			ChatService chatService,
			RoomCatalogWsService roomCatalogWsService
	) {
		this.roomRegistry = roomRegistry;
		this.roomProperties = roomProperties;
		this.sessionRegistry = sessionRegistry;
		this.gameService = gameService;
		this.chatService = chatService;
		this.roomCatalogWsService = roomCatalogWsService;
	}

	public RoomJoinResponseDto joinRoom(String username, String roomId) {
		if (!sessionRegistry.hasActiveLobbySession(username)) {
			throw new ConflictException("Active lobby WebSocket session is required", ERROR_CODE_LOBBY_SESSION_REQUIRED);
		}

		RoomRegistryEntry roomEntry = roomRegistry.findEntry(roomId);
		if (roomEntry == null) {
			throw new NotFoundException("Room not found", ERROR_CODE_ROOM_NOT_FOUND);
		}
		if (roomEntry.room().getPlayerCount() >= roomProperties.maxPlayersPerRoom()) {
			throw new ConflictException("Room is full", ERROR_CODE_ROOM_FULL);
		}

		gameService.prepareRoomJoin(username, roomId);
		if (!sessionRegistry.bindToRoom(username, roomId)) {
			throw new ConflictException("Active lobby WebSocket session is required", ERROR_CODE_LOBBY_SESSION_REQUIRED);
		}
		chatService.moveUserToRoom(username, roomId);
		roomCatalogWsService.publishRoomsUpdated();

		RoomJoinResponseDto response = new RoomJoinResponseDto(
				roomEntry.id(),
				roomEntry.mapId(),
				roomEntry.mapName(),
				roomEntry.room().getPlayerCount(),
				roomProperties.maxPlayersPerRoom(),
				"JOINED"
		);
		gameService.replyToPlayer(username, new MessageOutput(MessageType.ROOM_JOINED, response));
		gameService.replyToPlayer(username, new MessageOutput(
				MessageType.SPAWN_ASSIGNED,
				new SpawnAssignedResponseDto(roomEntry.id(), "INITIAL", 0.0, 0.0, 0.0)
		));
		gameService.activateRoomJoin(username, roomId);
		return response;
	}
}
