package ru.sea.patrol.service.game.catalog;

import java.util.ArrayList;
import java.util.List;

public record MerchantDefinition(
		String id,
		String name,
		String homePoiId,
		List<String> inventoryItemIds
) {

	public MerchantDefinition {
		id = normalizeRequired(id, "Merchant id");
		name = normalizeRequired(name, "Merchant name");
		homePoiId = normalizeRequired(homePoiId, "Merchant homePoiId");
		List<String> normalizedInventory = new ArrayList<>();
		for (String itemId : inventoryItemIds == null ? List.<String>of() : inventoryItemIds) {
			normalizedInventory.add(normalizeRequired(itemId, "Merchant inventory item id"));
		}
		if (normalizedInventory.isEmpty()) {
			throw new IllegalArgumentException("Merchant inventoryItemIds must not be empty");
		}
		inventoryItemIds = List.copyOf(normalizedInventory);
	}

	private static String normalizeRequired(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value.trim();
	}
}
