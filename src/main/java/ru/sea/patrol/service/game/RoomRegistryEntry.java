package ru.sea.patrol.service.game;

public record RoomRegistryEntry(
		String id,
		String name,
		String mapId,
		String mapName,
		GameRoom room
) {
}
