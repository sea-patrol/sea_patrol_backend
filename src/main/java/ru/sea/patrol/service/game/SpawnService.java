package ru.sea.patrol.service.game;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class SpawnService {

	static final double INITIAL_SPAWN_ORIGIN_X = 0.0;
	static final double INITIAL_SPAWN_ORIGIN_Z = 0.0;
	static final double INITIAL_SPAWN_ANGLE = 0.0;
	static final double INITIAL_SPAWN_MAX_OFFSET = 30.0;
	static final double INITIAL_SPAWN_MIN_X = -30.0;
	static final double INITIAL_SPAWN_MAX_X = 30.0;
	static final double INITIAL_SPAWN_MIN_Z = -30.0;
	static final double INITIAL_SPAWN_MAX_Z = 30.0;
	static final int INITIAL_SPAWN_MAX_ATTEMPTS = 16;

	public SpawnPoint calculateInitialSpawn() {
		for (int attempt = 0; attempt < INITIAL_SPAWN_MAX_ATTEMPTS; attempt++) {
			SpawnPoint candidate = new SpawnPoint(
					INITIAL_SPAWN_ORIGIN_X + randomOffset(),
					INITIAL_SPAWN_ORIGIN_Z + randomOffset(),
					INITIAL_SPAWN_ANGLE
			);
			if (isWithinBounds(candidate)) {
				return candidate;
			}
		}

		return new SpawnPoint(
				clamp(INITIAL_SPAWN_ORIGIN_X, INITIAL_SPAWN_MIN_X, INITIAL_SPAWN_MAX_X),
				clamp(INITIAL_SPAWN_ORIGIN_Z, INITIAL_SPAWN_MIN_Z, INITIAL_SPAWN_MAX_Z),
				INITIAL_SPAWN_ANGLE
		);
	}

	boolean isWithinBounds(SpawnPoint spawnPoint) {
		return isWithinBounds(spawnPoint.x(), spawnPoint.z());
	}

	boolean isWithinBounds(double x, double z) {
		return x >= INITIAL_SPAWN_MIN_X
				&& x <= INITIAL_SPAWN_MAX_X
				&& z >= INITIAL_SPAWN_MIN_Z
				&& z <= INITIAL_SPAWN_MAX_Z;
	}

	private double randomOffset() {
		return ThreadLocalRandom.current().nextDouble(-INITIAL_SPAWN_MAX_OFFSET, INITIAL_SPAWN_MAX_OFFSET);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
