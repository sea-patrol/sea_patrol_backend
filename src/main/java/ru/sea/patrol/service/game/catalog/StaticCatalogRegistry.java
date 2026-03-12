package ru.sea.patrol.service.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class StaticCatalogRegistry {

	static final String DEFAULT_BASE_PATH = "classpath:catalogs";

	private final List<ShipClassDefinition> shipClasses;
	private final List<ItemDefinition> items;
	private final List<MerchantDefinition> merchants;
	private final List<QuestDefinition> quests;

	private final Map<String, ShipClassDefinition> shipClassesById;
	private final Map<String, ItemDefinition> itemsById;
	private final Map<String, MerchantDefinition> merchantsById;
	private final Map<String, QuestDefinition> questsById;

	@Autowired
	public StaticCatalogRegistry(ObjectMapper objectMapper) {
		this(objectMapper, new PathMatchingResourcePatternResolver(), DEFAULT_BASE_PATH);
	}

	public StaticCatalogRegistry(
			ObjectMapper objectMapper,
			ResourcePatternResolver resourcePatternResolver,
			String basePath
	) {
		this.shipClasses = loadCatalog(
				objectMapper,
				resourcePatternResolver,
				basePath,
				"ship-classes.json",
				ShipClassCatalogFile.class,
				ShipClassCatalogFile::shipClasses,
				"ship classes"
		);
		this.items = loadCatalog(
				objectMapper,
				resourcePatternResolver,
				basePath,
				"items.json",
				ItemCatalogFile.class,
				ItemCatalogFile::items,
				"items"
		);
		this.merchants = loadCatalog(
				objectMapper,
				resourcePatternResolver,
				basePath,
				"merchants.json",
				MerchantCatalogFile.class,
				MerchantCatalogFile::merchants,
				"merchants"
		);
		this.quests = loadCatalog(
				objectMapper,
				resourcePatternResolver,
				basePath,
				"quests.json",
				QuestCatalogFile.class,
				QuestCatalogFile::quests,
				"quests"
		);

		this.shipClassesById = indexById(shipClasses, ShipClassDefinition::id, "ship class");
		this.itemsById = indexById(items, ItemDefinition::id, "item");
		this.merchantsById = indexById(merchants, MerchantDefinition::id, "merchant");
		this.questsById = indexById(quests, QuestDefinition::id, "quest");

		validateMerchantReferences();
		validateQuestReferences();
	}

	public List<ShipClassDefinition> shipClasses() {
		return shipClasses;
	}

	public List<ItemDefinition> items() {
		return items;
	}

	public List<MerchantDefinition> merchants() {
		return merchants;
	}

	public List<QuestDefinition> quests() {
		return quests;
	}

	public Optional<ShipClassDefinition> getShipClass(String id) {
		return resolve(shipClassesById, id);
	}

	public Optional<ItemDefinition> getItem(String id) {
		return resolve(itemsById, id);
	}

	public Optional<MerchantDefinition> getMerchant(String id) {
		return resolve(merchantsById, id);
	}

	public Optional<QuestDefinition> getQuest(String id) {
		return resolve(questsById, id);
	}

	private void validateMerchantReferences() {
		for (MerchantDefinition merchant : merchants) {
			for (String itemId : merchant.inventoryItemIds()) {
				if (!itemsById.containsKey(itemId)) {
					throw new IllegalStateException(
							"Unknown merchant inventory itemId " + itemId + " for merchant " + merchant.id()
					);
				}
			}
		}
	}

	private void validateQuestReferences() {
		for (QuestDefinition quest : quests) {
			if (quest.objectiveType().requiresItemReference() && !itemsById.containsKey(quest.targetId())) {
				throw new IllegalStateException(
						"Unknown quest target itemId " + quest.targetId() + " for quest " + quest.id()
				);
			}
		}
	}

	private static <T, C> List<T> loadCatalog(
			ObjectMapper objectMapper,
			ResourcePatternResolver resourcePatternResolver,
			String basePath,
			String fileName,
			Class<C> catalogType,
			Function<C, List<T>> extractor,
			String catalogName
	) {
		Resource resource = resourcePatternResolver.getResource(basePath + "/" + fileName);
		if (!resource.exists()) {
			throw new IllegalStateException("Missing static catalog resource: " + basePath + "/" + fileName);
		}

		try (InputStream inputStream = resource.getInputStream()) {
			C catalogFile = objectMapper.readValue(inputStream, catalogType);
			List<T> definitions = List.copyOf(extractor.apply(catalogFile));
			if (definitions.isEmpty()) {
				throw new IllegalStateException("Static catalog must not be empty: " + catalogName);
			}
			return definitions;
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to load static catalog " + catalogName, exception);
		}
	}

	private static <T> Map<String, T> indexById(List<T> values, Function<T, String> idExtractor, String label) {
		Map<String, T> indexed = new LinkedHashMap<>();
		for (T value : values) {
			String id = idExtractor.apply(value);
			if (indexed.putIfAbsent(id, value) != null) {
				throw new IllegalStateException("Duplicate " + label + " id: " + id);
			}
		}
		return Map.copyOf(indexed);
	}

	private static <T> Optional<T> resolve(Map<String, T> valuesById, String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(valuesById.get(id.trim()));
	}

	private record ShipClassCatalogFile(List<ShipClassDefinition> shipClasses) {
	}

	private record ItemCatalogFile(List<ItemDefinition> items) {
	}

	private record MerchantCatalogFile(List<MerchantDefinition> merchants) {
	}

	private record QuestCatalogFile(List<QuestDefinition> quests) {
	}
}
