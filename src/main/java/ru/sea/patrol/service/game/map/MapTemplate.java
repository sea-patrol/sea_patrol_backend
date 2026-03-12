package ru.sea.patrol.service.game.map;

import java.util.List;
import java.util.Objects;

public record MapTemplate(
		String id,
		String name,
		String region,
		int revision,
		boolean defaultMap,
		boolean enabled,
		Bounds bounds,
		SpawnRules spawnRules,
		FileSet files,
		Presentation presentation,
		WindSettings defaultWind,
		List<Collider> colliders,
		List<SpawnPoint> spawnPoints,
		List<PointOfInterest> pointsOfInterest,
		Minimap minimap
) {

	private static final String MAP_ID_PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

	public MapTemplate {
		id = normalizeRequired(id, "Map id");
		name = normalizeRequired(name, "Map name");
		region = normalizeRequired(region, "Map region");
		if (!id.matches(MAP_ID_PATTERN)) {
			throw new IllegalArgumentException("Map id must match " + MAP_ID_PATTERN);
		}
		if (revision <= 0) {
			throw new IllegalArgumentException("Map revision must be greater than zero");
		}
		if (defaultMap && !enabled) {
			throw new IllegalArgumentException("Default map must be enabled");
		}
		bounds = Objects.requireNonNull(bounds, "Map bounds must not be null");
		spawnRules = Objects.requireNonNull(spawnRules, "Map spawn rules must not be null");
		files = Objects.requireNonNull(files, "Map files must not be null");
		presentation = Objects.requireNonNull(presentation, "Map presentation must not be null");
		defaultWind = Objects.requireNonNull(defaultWind, "Map default wind must not be null");
		colliders = List.copyOf(Objects.requireNonNull(colliders, "Map colliders must not be null"));
		spawnPoints = List.copyOf(Objects.requireNonNull(spawnPoints, "Map spawn points must not be null"));
		pointsOfInterest = List.copyOf(Objects.requireNonNull(pointsOfInterest, "Map points of interest must not be null"));
		minimap = Objects.requireNonNull(minimap, "Map minimap metadata must not be null");
		if (spawnPoints.isEmpty()) {
			throw new IllegalArgumentException("Map must define at least one player spawn point");
		}
	}

	public static MapTemplate mvpDefault() {
		return new MapTemplate(
				"caribbean-01",
				"Caribbean Sea",
				"caribbean",
				1,
				true,
				true,
				new Bounds(-5000.0, 5000.0, -5000.0, 5000.0),
				new SpawnRules(PlayerSpawnMode.POINTS, 30.0, false, false),
				new FileSet("colliders.json", "spawn-points.json", "poi.json", "minimap.json"),
				new Presentation("tropical", null),
				new WindSettings(0.0, 10.0),
				List.of(new Collider(
						"fallback-island",
						"POLYGON",
						List.of(
								new Point(-220.0, 140.0),
								new Point(-120.0, 260.0),
								new Point(40.0, 180.0),
								new Point(-60.0, 60.0)
						)
				)),
				List.of(new SpawnPoint("fallback-center", 0.0, 0.0, 0.0)),
				List.of(new PointOfInterest("fallback-port", "Fallback Port", "PORT", 120.0, -80.0)),
				new Minimap(null, new Calibration(-5000.0, 5000.0, -5000.0, 5000.0))
		);
	}

	private static String normalizeRequired(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value.trim();
	}

	private static String normalizeOptional(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	public record Bounds(double minX, double maxX, double minZ, double maxZ) {
		public Bounds {
			if (minX >= maxX) {
				throw new IllegalArgumentException("Map bounds require minX < maxX");
			}
			if (minZ >= maxZ) {
				throw new IllegalArgumentException("Map bounds require minZ < maxZ");
			}
		}
	}

	public enum PlayerSpawnMode {
		POINTS
	}

	public record SpawnRules(
			PlayerSpawnMode playerSpawnMode,
			double playerSpawnRadius,
			boolean npcSpawnEnabled,
			boolean resourceSpawnEnabled
	) {
		public SpawnRules {
			Objects.requireNonNull(playerSpawnMode, "Map player spawn mode must not be null");
			if (playerSpawnRadius < 0.0) {
				throw new IllegalArgumentException("Player spawn radius must be zero or positive");
			}
		}
	}

	public record FileSet(String colliders, String spawnPoints, String poi, String minimap) {
		public FileSet {
			colliders = normalizeRequired(colliders, "Collider file path");
			spawnPoints = normalizeRequired(spawnPoints, "Spawn points file path");
			poi = normalizeRequired(poi, "POI file path");
			minimap = normalizeRequired(minimap, "Minimap file path");
		}
	}

	public record Presentation(String theme, String previewImage) {
		public Presentation {
			theme = normalizeRequired(theme, "Presentation theme");
			previewImage = normalizeOptional(previewImage);
		}
	}

	public record WindSettings(double angle, double speed) {
		public WindSettings {
			if (speed < 0.0) {
				throw new IllegalArgumentException("Default wind speed must be zero or positive");
			}
		}
	}

	public record Collider(String id, String kind, List<Point> points) {
		public Collider {
			id = normalizeRequired(id, "Collider id");
			kind = normalizeRequired(kind, "Collider kind");
			points = List.copyOf(Objects.requireNonNull(points, "Collider points must not be null"));
			if (points.size() < 3) {
				throw new IllegalArgumentException("Collider polygon must contain at least three points");
			}
		}
	}

	public record Point(double x, double z) {
	}

	public record SpawnPoint(String id, double x, double z, double angle) {
		public SpawnPoint {
			id = normalizeRequired(id, "Spawn point id");
		}
	}

	public record PointOfInterest(String id, String name, String kind, double x, double z) {
		public PointOfInterest {
			id = normalizeRequired(id, "POI id");
			name = normalizeRequired(name, "POI name");
			kind = normalizeRequired(kind, "POI kind");
		}
	}

	public record Minimap(String overlayImage, Calibration calibration) {
		public Minimap {
			overlayImage = normalizeOptional(overlayImage);
			calibration = Objects.requireNonNull(calibration, "Minimap calibration must not be null");
		}
	}

	public record Calibration(double worldMinX, double worldMaxX, double worldMinZ, double worldMaxZ) {
		public Calibration {
			if (worldMinX >= worldMaxX) {
				throw new IllegalArgumentException("Minimap calibration requires worldMinX < worldMaxX");
			}
			if (worldMinZ >= worldMaxZ) {
				throw new IllegalArgumentException("Minimap calibration requires worldMinZ < worldMaxZ");
			}
		}
	}
}