package ru.sea.patrol.service.game;

import org.springframework.stereotype.Service;
import ru.sea.patrol.error.domain.ApiException;
import ru.sea.patrol.error.domain.ConflictException;
import ru.sea.patrol.room.api.dto.RoomCatalogResponseDto;
import ru.sea.patrol.room.api.dto.RoomCreateRequestDto;
import ru.sea.patrol.room.api.dto.RoomSummaryDto;

@Service
public class RoomCatalogService {

	public static final String DEFAULT_MAP_ID = "caribbean-01";
	public static final String DEFAULT_MAP_NAME = "Caribbean Sea";

	private static final String ERROR_CODE_INVALID_MAP_ID = "INVALID_MAP_ID";
	private static final String ERROR_CODE_MAX_ROOMS_REACHED = "MAX_ROOMS_REACHED";

	private final RoomRegistry roomRegistry;
	private final GameRoomProperties roomProperties;

	public RoomCatalogService(RoomRegistry roomRegistry, GameRoomProperties roomProperties) {
		this.roomRegistry = roomRegistry;
		this.roomProperties = roomProperties;
	}

	public RoomCatalogResponseDto getCatalog() {
		var rooms = roomRegistry.roomsSnapshot().stream()
				.sorted(java.util.Comparator.comparing(RoomRegistryEntry::id))
				.map(this::toSummary)
				.toList();

		return new RoomCatalogResponseDto(
				roomProperties.maxRooms(),
				roomProperties.maxPlayersPerRoom(),
				rooms
		);
	}

	public RoomSummaryDto createRoom(RoomCreateRequestDto request) {
		if (roomRegistry.roomCount() >= roomProperties.maxRooms()) {
			throw new ConflictException("Maximum number of rooms reached", ERROR_CODE_MAX_ROOMS_REACHED);
		}

		String mapId = normalizeMapId(request == null ? null : request.getMapId());
		var createdRoom = roomRegistry.createRoom(
				normalizeName(request == null ? null : request.getName()),
				mapId,
				resolveMapName(mapId)
		);
		return toSummary(createdRoom);
	}

	private RoomSummaryDto toSummary(RoomRegistryEntry room) {
		int currentPlayers = room.room().getPlayerCount();
		int maxPlayers = roomProperties.maxPlayersPerRoom();
		String status = currentPlayers >= maxPlayers ? "FULL" : "OPEN";

		return new RoomSummaryDto(
				room.id(),
				room.name(),
				room.mapId(),
				room.mapName(),
				currentPlayers,
				maxPlayers,
				status
		);
	}

	private static String normalizeMapId(String requestedMapId) {
		if (requestedMapId == null || requestedMapId.isBlank()) {
			return DEFAULT_MAP_ID;
		}
		String mapId = requestedMapId.trim();
		if (!DEFAULT_MAP_ID.equals(mapId)) {
			throw new ApiException("Unknown mapId", ERROR_CODE_INVALID_MAP_ID);
		}
		return mapId;
	}

	private static String resolveMapName(String mapId) {
		return DEFAULT_MAP_ID.equals(mapId) ? DEFAULT_MAP_NAME : DEFAULT_MAP_NAME;
	}

	private static String normalizeName(String requestedName) {
		if (requestedName == null || requestedName.isBlank()) {
			return null;
		}
		return requestedName.trim();
	}
}
