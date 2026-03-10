package ru.sea.patrol.service.game.map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;

class MapTemplateRegistryTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

	@Test
	void loadsDefaultMapFromProductionResources() {
		MapTemplateRegistry registry = new MapTemplateRegistry(
				objectMapper,
				resourcePatternResolver,
				"classpath*:worlds/*/manifest.json"
		);

		assertThat(registry.list())
				.extracting(MapTemplate::id)
				.containsExactly("caribbean-01");
		assertThat(registry.defaultMap().id()).isEqualTo("caribbean-01");
		assertThat(registry.defaultMap().name()).isEqualTo("Caribbean Sea");
	}

	@Test
	void skipsBrokenTemplatesAndKeepsOnlyValidMaps() {
		MapTemplateRegistry registry = new MapTemplateRegistry(
				objectMapper,
				resourcePatternResolver,
				"classpath*:test-worlds/*/manifest.json"
		);

		assertThat(registry.list())
				.extracting(MapTemplate::id)
				.containsExactly("caribbean-01", "test-sandbox-01");
		assertThat(registry.get("test-sandbox-01"))
				.map(MapTemplate::name)
				.contains("Test Sandbox");
		assertThat(registry.get("broken map")).isEmpty();
		assertThat(registry.defaultMap().id()).isEqualTo("caribbean-01");
	}
}