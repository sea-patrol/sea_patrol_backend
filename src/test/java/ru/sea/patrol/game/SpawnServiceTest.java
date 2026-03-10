package ru.sea.patrol.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpawnServiceTest {

	@Test
	void calculateInitialSpawn_returnsRandomPointInsideDefaultBounds() {
		SpawnService spawnService = new SpawnService();

		boolean foundNonZeroOffset = false;
		for (int i = 0; i < 50; i++) {
			SpawnPoint spawnPoint = spawnService.calculateInitialSpawn();
			assertThat(spawnPoint.x()).isBetween(-30.0, 30.0);
			assertThat(spawnPoint.z()).isBetween(-30.0, 30.0);
			assertThat(spawnPoint.angle()).isEqualTo(0.0);
			foundNonZeroOffset = foundNonZeroOffset || Math.abs(spawnPoint.x()) > 0.1 || Math.abs(spawnPoint.z()) > 0.1;
		}

		assertThat(foundNonZeroOffset).isTrue();
	}

	@Test
	void isWithinBounds_rejectsOutOfRangeCoordinates() {
		SpawnService spawnService = new SpawnService();

		assertThat(spawnService.isWithinBounds(new SpawnPoint(0.0, 0.0, 0.0))).isTrue();
		assertThat(spawnService.isWithinBounds(new SpawnPoint(31.0, 0.0, 0.0))).isFalse();
		assertThat(spawnService.isWithinBounds(new SpawnPoint(0.0, -31.0, 0.0))).isFalse();
	}
}
