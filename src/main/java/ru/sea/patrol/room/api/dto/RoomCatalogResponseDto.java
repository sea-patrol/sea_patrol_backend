package ru.sea.patrol.room.api.dto;

import java.util.List;

public record RoomCatalogResponseDto(
		int maxRooms,
		int maxPlayersPerRoom,
		List<RoomSummaryDto> rooms
) {
}
