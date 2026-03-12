package ru.sea.patrol.service.game.catalog;

public record ItemDefinition(
		String id,
		String name,
		ItemKind kind,
		boolean stackable,
		int maxStack,
		int basePrice
) {

	public ItemDefinition {
		id = normalizeRequired(id, "Item id");
		name = normalizeRequired(name, "Item name");
		if (kind == null) {
			throw new IllegalArgumentException("Item kind must not be null");
		}
		if (maxStack <= 0) {
			throw new IllegalArgumentException("Item maxStack must be greater than zero");
		}
		if (!stackable && maxStack != 1) {
			throw new IllegalArgumentException("Non-stackable item must have maxStack = 1");
		}
		if (basePrice < 0) {
			throw new IllegalArgumentException("Item basePrice must be zero or positive");
		}
	}

	private static String normalizeRequired(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value.trim();
	}
}
