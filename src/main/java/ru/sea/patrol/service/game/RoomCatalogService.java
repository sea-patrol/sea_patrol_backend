package ru.sea.patrol.service.game;

import org.springframework.stereotype.Service;
import ru.sea.patrol.error.domain.ApiException;
import ru.sea.patrol.error.domain.ConflictException;
import ru.sea.patrol.room.api.dto.RoomCatalogResponseDto;
import ru.sea.patrol.room.api.dto.RoomCreateRequestDto;
import ru.sea.patrol.room.api.dto.RoomSummaryDto;
import ru.sea.patrol.service.game.map.MapTemplate;
import ru.sea.patrol.service.game.map.MapTemplateRegistry;

@Service
public class RoomCatalogService {

	private static final String ERROR_CODE_INVALID_MAP_ID = "INVALID_MAP_ID";
	private static final String ERROR_CODE_MAX_ROOMS_REACHED = "MAX_ROOMS_REACHED";

	private final RoomRegistry roomRegistry;
	private final GameRoomProperties roomProperties;
	private final MapTemplateRegistry mapTemplateRegistry;

	public RoomCatalogService(
			RoomRegistry roomRegistry,
			GameRoomProperties roomProperties,
			MapTemplateRegistry mapTemplateRegistry
	) {
		this.roomRegistry = roomRegistry;
		this.roomProperties = roomProperties;
		this.mapTemplateRegistry = mapTemplateRegistry;
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

		MapTemplate mapTemplate = resolveMapTemplate(request == null ? null : request.getMapId());
		var createdRoom = roomRegistry.createRoom(
				normalizeName(request == null ? null : request.getName()),
				mapTemplate
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

	private MapTemplate resolveMapTemplate(String requestedMapId) {
		if (requestedMapId == null || requestedMapId.isBlank()) {
			return mapTemplateRegistry.defaultMap();
		}
		return mapTemplateRegistry.get(requestedMapId)
				.orElseThrow(() -> new ApiException("Unknown mapId", ERROR_CODE_INVALID_MAP_ID));
	}

	private static String normalizeName(String requestedName) {
		if (requestedName == null || requestedName.isBlank()) {
			return null;
		}
		return requestedName.trim();
	}
}
