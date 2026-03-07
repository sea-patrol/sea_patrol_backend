package ru.sea.patrol.ws.protocol.dto;

public record RoomJoinResponseDto(
		String roomId,
		String mapId,
		String mapName,
		int currentPlayers,
		int maxPlayers,
		String status
) {
}
