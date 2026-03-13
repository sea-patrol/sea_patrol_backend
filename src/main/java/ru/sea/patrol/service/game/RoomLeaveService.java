package ru.sea.patrol.service.game;

import org.springframework.stereotype.Service;
import ru.sea.patrol.error.domain.ConflictException;
import ru.sea.patrol.error.domain.NotFoundException;
import ru.sea.patrol.room.api.dto.RoomLeaveResponseDto;
import ru.sea.patrol.service.chat.ChatService;
import ru.sea.patrol.service.session.GameSessionRegistry;

@Service
public class RoomLeaveService {

	private static final String ERROR_CODE_ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
	private static final String ERROR_CODE_ROOM_SESSION_REQUIRED = "ROOM_SESSION_REQUIRED";
	private static final String ERROR_CODE_ROOM_SESSION_MISMATCH = "ROOM_SESSION_MISMATCH";

	private final RoomRegistry roomRegistry;
	private final GameSessionRegistry sessionRegistry;
	private final GameService gameService;
	private final ChatService chatService;
	private final RoomCatalogWsService roomCatalogWsService;

	public RoomLeaveService(
			RoomRegistry roomRegistry,
			GameSessionRegistry sessionRegistry,
			GameService gameService,
			ChatService chatService,
			RoomCatalogWsService roomCatalogWsService
	) {
		this.roomRegistry = roomRegistry;
		this.sessionRegistry = sessionRegistry;
		this.gameService = gameService;
		this.chatService = chatService;
		this.roomCatalogWsService = roomCatalogWsService;
	}

	public RoomLeaveResponseDto leaveRoom(String username, String roomId) {
		RoomRegistryEntry roomEntry = roomRegistry.findEntry(roomId);
		if (roomEntry == null) {
			throw new NotFoundException("Room not found", ERROR_CODE_ROOM_NOT_FOUND);
		}

		GameSessionRegistry.LobbyRebindResult rebindResult = sessionRegistry.bindToLobbyFromRoom(username, roomId);
		if (rebindResult == GameSessionRegistry.LobbyRebindResult.ROOM_SESSION_REQUIRED) {
			throw new ConflictException("Active room WebSocket session is required", ERROR_CODE_ROOM_SESSION_REQUIRED);
		}
		if (rebindResult == GameSessionRegistry.LobbyRebindResult.ROOM_SESSION_MISMATCH) {
			throw new ConflictException("Player is not bound to this room", ERROR_CODE_ROOM_SESSION_MISMATCH);
		}

		gameService.leaveRoom(username, roomId);
		chatService.moveUserToLobby(username, roomId);
		gameService.replyToPlayer(username, roomCatalogWsService.snapshotMessage());
		roomCatalogWsService.publishRoomsUpdated();

		return new RoomLeaveResponseDto(roomId, "LEFT", "LOBBY");
	}
}
