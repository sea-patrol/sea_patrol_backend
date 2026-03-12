package ru.sea.patrol.service.game.map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;

class MapTemplateRegistryTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

	@Test
	void loadsFullCaribbeanPackageFromProductionResources() {
		MapTemplateRegistry registry = new MapTemplateRegistry(
				objectMapper,
				resourcePatternResolver,
				"classpath*:worlds/*/manifest.json"
		);

		MapTemplate caribbean = registry.defaultMap();
		assertThat(registry.list())
				.extracting(MapTemplate::id)
				.containsExactly("caribbean-01");
		assertThat(caribbean.id()).isEqualTo("caribbean-01");
		assertThat(caribbean.name()).isEqualTo("Caribbean Sea");
		assertThat(caribbean.region()).isEqualTo("caribbean");
		assertThat(caribbean.revision()).isEqualTo(1);
		assertThat(caribbean.bounds().minX()).isEqualTo(-5000.0);
		assertThat(caribbean.spawnRules().playerSpawnMode()).isEqualTo(MapTemplate.PlayerSpawnMode.POINTS);
		assertThat(caribbean.spawnRules().playerSpawnRadius()).isEqualTo(30.0);
		assertThat(caribbean.defaultWind().speed()).isEqualTo(10.0);
		assertThat(caribbean.colliders()).isNotEmpty();
		assertThat(caribbean.spawnPoints()).hasSize(1);
		assertThat(caribbean.pointsOfInterest()).isNotEmpty();
		assertThat(caribbean.minimap().calibration().worldMaxX()).isEqualTo(5000.0);
	}

	@Test
	void skipsBrokenTemplatesAndKeepsOnlyValidPackages() {
		MapTemplateRegistry registry = new MapTemplateRegistry(
				objectMapper,
				resourcePatternResolver,
				"classpath*:test-worlds/*/manifest.json"
		);

		assertThat(registry.list())
				.extracting(MapTemplate::id)
				.containsExactly("caribbean-01", "test-sandbox-01");
		assertThat(registry.get("test-sandbox-01"))
				.map(MapTemplate::region)
				.contains("dev");
		assertThat(registry.get("broken-missing-spawns")).isEmpty();
		assertThat(registry.defaultMap().id()).isEqualTo("caribbean-01");
	}
}