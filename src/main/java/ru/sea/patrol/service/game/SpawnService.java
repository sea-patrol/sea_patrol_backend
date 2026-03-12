package ru.sea.patrol.service.game;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import ru.sea.patrol.service.game.map.MapTemplate;

@Service
public class SpawnService {

	static final int INITIAL_SPAWN_MAX_ATTEMPTS = 16;

	public SpawnPoint calculateInitialSpawn() {
		return calculateInitialSpawn(MapTemplate.mvpDefault());
	}

	public SpawnPoint calculateInitialSpawn(MapTemplate mapTemplate) {
		MapTemplate resolvedTemplate = mapTemplate == null ? MapTemplate.mvpDefault() : mapTemplate;
		MapTemplate.SpawnPoint anchor = selectAnchor(resolvedTemplate);
		MapTemplate.Bounds bounds = resolvedTemplate.bounds();
		double spawnRadius = resolvedTemplate.spawnRules().playerSpawnRadius();

		for (int attempt = 0; attempt < INITIAL_SPAWN_MAX_ATTEMPTS; attempt++) {
			SpawnPoint candidate = new SpawnPoint(
					anchor.x() + randomOffset(spawnRadius),
					anchor.z() + randomOffset(spawnRadius),
					anchor.angle()
			);
			if (isWithinBounds(candidate, bounds)) {
				return candidate;
			}
		}

		return new SpawnPoint(
				clamp(anchor.x(), bounds.minX(), bounds.maxX()),
				clamp(anchor.z(), bounds.minZ(), bounds.maxZ()),
				anchor.angle()
		);
	}

	public boolean isWithinBounds(SpawnPoint spawnPoint) {
		return isWithinBounds(spawnPoint, MapTemplate.mvpDefault().bounds());
	}

	public boolean isWithinBounds(SpawnPoint spawnPoint, MapTemplate.Bounds bounds) {
		return isWithinBounds(spawnPoint.x(), spawnPoint.z(), bounds);
	}

	public boolean isWithinBounds(double x, double z) {
		return isWithinBounds(x, z, MapTemplate.mvpDefault().bounds());
	}

	public boolean isWithinBounds(double x, double z, MapTemplate.Bounds bounds) {
		return x >= bounds.minX()
				&& x <= bounds.maxX()
				&& z >= bounds.minZ()
				&& z <= bounds.maxZ();
	}

	private static MapTemplate.SpawnPoint selectAnchor(MapTemplate mapTemplate) {
		var spawnPoints = mapTemplate.spawnPoints();
		return spawnPoints.get(ThreadLocalRandom.current().nextInt(spawnPoints.size()));
	}

	private double randomOffset(double radius) {
		if (radius <= 0.0) {
			return 0.0;
		}
		return ThreadLocalRandom.current().nextDouble(-radius, radius);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
