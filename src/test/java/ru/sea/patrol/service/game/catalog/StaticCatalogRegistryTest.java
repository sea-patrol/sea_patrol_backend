package ru.sea.patrol.service.game.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;

class StaticCatalogRegistryTest {

	@Test
	void loadsStaticCatalogsFromResources() {
		StaticCatalogRegistry registry = new StaticCatalogRegistry(
				new ObjectMapper(),
				new PathMatchingResourcePatternResolver(),
				"classpath:catalogs"
		);

		assertThat(registry.shipClasses()).hasSize(3);
		assertThat(registry.items()).hasSize(6);
		assertThat(registry.merchants()).hasSize(2);
		assertThat(registry.quests()).hasSize(3);

		assertThat(registry.getShipClass("starter-sloop"))
				.get()
				.extracting(ShipClassDefinition::maxCargoSlots)
				.isEqualTo(12);
		assertThat(registry.getItem("repair-kit"))
				.get()
				.extracting(ItemDefinition::basePrice)
				.isEqualTo(35);
		assertThat(registry.getMerchant("port-royal-trader"))
				.get()
				.extracting(MerchantDefinition::homePoiId)
				.isEqualTo("port-royal");
		assertThat(registry.getQuest("deliver-wood-01"))
				.get()
				.extracting(QuestDefinition::objectiveType, QuestDefinition::targetId)
				.containsExactly(QuestObjectiveType.DELIVER_ITEM, "wood-log");
	}

	@Test
	void failsFastWhenMerchantReferencesUnknownItem() {
		assertThatThrownBy(() -> new StaticCatalogRegistry(
				new ObjectMapper(),
				new PathMatchingResourcePatternResolver(),
				"classpath:test-catalogs-broken-merchant"
		))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Unknown merchant inventory itemId missing-item");
	}
}
