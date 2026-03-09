package ru.sea.patrol.room.api.dto;

public class RoomCreateRequestDto {

	private String name;
	private String mapId;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getMapId() {
		return mapId;
	}

	public void setMapId(String mapId) {
		this.mapId = mapId;
	}
}
