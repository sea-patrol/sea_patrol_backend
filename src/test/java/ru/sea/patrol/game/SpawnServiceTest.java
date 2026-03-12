package ru.sea.patrol.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.sea.patrol.service.game.SpawnPoint;
import ru.sea.patrol.service.game.SpawnService;
import ru.sea.patrol.service.game.map.MapTemplate;

class SpawnServiceTest {

	@Test
	void calculateInitialSpawn_usesTemplateSpawnPointRadiusAndBounds() {
		SpawnService spawnService = new SpawnService();
		MapTemplate mapTemplate = new MapTemplate(
				"test-sandbox-01",
				"Test Sandbox",
				"dev",
				1,
				false,
				true,
				new MapTemplate.Bounds(-1000.0, 1000.0, -1000.0, 1000.0),
				new MapTemplate.SpawnRules(MapTemplate.PlayerSpawnMode.POINTS, 15.0, false, false),
				new MapTemplate.FileSet("colliders.json", "spawn-points.json", "poi.json", "minimap.json"),
				new MapTemplate.Presentation("debug", null),
				new MapTemplate.WindSettings(1.57, 4.0),
				MapTemplate.mvpDefault().colliders(),
				java.util.List.of(new MapTemplate.SpawnPoint("debug-spawn", 10.0, -10.0, 0.5)),
				MapTemplate.mvpDefault().pointsOfInterest(),
				MapTemplate.mvpDefault().minimap()
		);

		boolean foundNonZeroOffset = false;
		for (int i = 0; i < 50; i++) {
			SpawnPoint spawnPoint = spawnService.calculateInitialSpawn(mapTemplate);
			assertThat(spawnPoint.x()).isBetween(-5.0, 25.0);
			assertThat(spawnPoint.z()).isBetween(-25.0, 5.0);
			assertThat(spawnPoint.angle()).isEqualTo(0.5);
			foundNonZeroOffset = foundNonZeroOffset || Math.abs(spawnPoint.x() - 10.0) > 0.1 || Math.abs(spawnPoint.z() + 10.0) > 0.1;
		}

		assertThat(foundNonZeroOffset).isTrue();
	}

	@Test
	void isWithinBounds_rejectsCoordinatesOutsideTemplateBounds() {
		SpawnService spawnService = new SpawnService();
		MapTemplate.Bounds bounds = new MapTemplate.Bounds(-1000.0, 1000.0, -1000.0, 1000.0);

		assertThat(spawnService.isWithinBounds(new SpawnPoint(10.0, -10.0, 0.5), bounds)).isTrue();
		assertThat(spawnService.isWithinBounds(new SpawnPoint(1001.0, 0.0, 0.0), bounds)).isFalse();
		assertThat(spawnService.isWithinBounds(new SpawnPoint(0.0, -1001.0, 0.0), bounds)).isFalse();
	}
}
