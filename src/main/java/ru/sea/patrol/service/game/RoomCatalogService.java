package ru.sea.patrol.service.game;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import ru.sea.patrol.room.api.dto.RoomCatalogResponseDto;
import ru.sea.patrol.room.api.dto.RoomSummaryDto;

@Service
public class RoomCatalogService {

	private static final String DEFAULT_MAP_ID = "caribbean-01";
	private static final String DEFAULT_MAP_NAME = "Caribbean Sea";

	private final RoomRegistry roomRegistry;
	private final GameRoomProperties roomProperties;

	public RoomCatalogService(RoomRegistry roomRegistry, GameRoomProperties roomProperties) {
		this.roomRegistry = roomRegistry;
		this.roomProperties = roomProperties;
	}

	public RoomCatalogResponseDto getCatalog() {
		List<RoomSummaryDto> rooms = roomRegistry.roomsSnapshot().stream()
				.sorted(Comparator.comparing(GameRoom::getName))
				.map(this::toSummary)
				.toList();

		return new RoomCatalogResponseDto(
				roomProperties.maxRooms(),
				roomProperties.maxPlayersPerRoom(),
				rooms
		);
	}

	private RoomSummaryDto toSummary(GameRoom room) {
		int currentPlayers = room.getPlayerCount();
		int maxPlayers = roomProperties.maxPlayersPerRoom();
		String status = currentPlayers >= maxPlayers ? "FULL" : "OPEN";

		return new RoomSummaryDto(
				room.getName(),
				room.getName(),
				DEFAULT_MAP_ID,
				DEFAULT_MAP_NAME,
				currentPlayers,
				maxPlayers,
				status
		);
	}
}
