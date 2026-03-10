package ru.sea.patrol.service.game.map;

public record MapTemplate(String id, String name, boolean defaultMap) {

	private static final String MAP_ID_PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

	public MapTemplate {
		id = normalize(id, "Map id");
		name = normalize(name, "Map name");
		if (!id.matches(MAP_ID_PATTERN)) {
			throw new IllegalArgumentException("Map id must match " + MAP_ID_PATTERN);
		}
	}

	public static MapTemplate mvpDefault() {
		return new MapTemplate("caribbean-01", "Caribbean Sea", true);
	}

	private static String normalize(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value.trim();
	}
}