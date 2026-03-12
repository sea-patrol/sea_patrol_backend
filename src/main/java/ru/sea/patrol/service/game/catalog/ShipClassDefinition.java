package ru.sea.patrol.service.game.catalog;

public record ShipClassDefinition(
		String id,
		String name,
		String hullModel,
		int maxHealth,
		int maxCargoSlots,
		double maxSpeed
) {

	public ShipClassDefinition {
		id = normalizeRequired(id, "Ship class id");
		name = normalizeRequired(name, "Ship class name");
		hullModel = normalizeRequired(hullModel, "Ship class hullModel");
		if (maxHealth <= 0) {
			throw new IllegalArgumentException("Ship class maxHealth must be greater than zero");
		}
		if (maxCargoSlots < 0) {
			throw new IllegalArgumentException("Ship class maxCargoSlots must be zero or positive");
		}
		if (maxSpeed <= 0.0) {
			throw new IllegalArgumentException("Ship class maxSpeed must be greater than zero");
		}
	}

	private static String normalizeRequired(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value.trim();
	}
}
