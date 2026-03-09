package ru.sea.patrol.room.api.dto;

public record RoomSummaryDto(
		String id,
		String name,
		String mapId,
		String mapName,
		int currentPlayers,
		int maxPlayers,
		String status
) {
}
