package ru.sea.patrol.ws.protocol.dto;

import ru.sea.patrol.service.game.map.MapTemplate;

public record RoomStateInfo(
		String roomId,
		String roomName,
		String mapId,
		String mapName,
		int mapRevision,
		String theme,
		RoomBoundsInfo bounds
) {

	public static RoomStateInfo from(String roomId, String roomName, MapTemplate mapTemplate) {
		return new RoomStateInfo(
				roomId,
				roomName,
				mapTemplate.id(),
				mapTemplate.name(),
				mapTemplate.revision(),
				mapTemplate.presentation().theme(),
				new RoomBoundsInfo(
						mapTemplate.bounds().minX(),
						mapTemplate.bounds().maxX(),
						mapTemplate.bounds().minZ(),
						mapTemplate.bounds().maxZ()
				)
		);
	}

	public record RoomBoundsInfo(
			double minX,
			double maxX,
			double minZ,
			double maxZ
	) {
	}
}
