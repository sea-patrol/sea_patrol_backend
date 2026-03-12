package ru.sea.patrol.service.game.map;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class MapTemplateRegistry {

	static final String DEFAULT_RESOURCE_PATTERN = "classpath*:worlds/*/manifest.json";

	private final List<MapTemplate> availableMaps;
	private final Map<String, MapTemplate> mapsById;
	private final MapTemplate defaultMap;

	@Autowired
	public MapTemplateRegistry(ObjectMapper objectMapper) {
		this(objectMapper, new PathMatchingResourcePatternResolver(), DEFAULT_RESOURCE_PATTERN);
	}

	public MapTemplateRegistry(
			ObjectMapper objectMapper,
			ResourcePatternResolver resourcePatternResolver,
			String resourcePattern
	) {
		List<MapTemplate> loadedMaps = loadMaps(objectMapper, resourcePatternResolver, resourcePattern);
		if (loadedMaps.isEmpty()) {
			throw new IllegalStateException("No valid map templates found under " + resourcePattern);
		}

		long defaultCount = loadedMaps.stream()
				.filter(MapTemplate::defaultMap)
				.count();
		if (defaultCount != 1) {
			throw new IllegalStateException("Exactly one default map template is required, but found " + defaultCount);
		}

		this.availableMaps = loadedMaps.stream()
				.sorted(Comparator.comparing(MapTemplate::id))
				.toList();
		this.mapsById = availableMaps.stream()
				.collect(Collectors.toUnmodifiableMap(MapTemplate::id, Function.identity()));
		this.defaultMap = availableMaps.stream()
				.filter(MapTemplate::defaultMap)
				.findFirst()
				.orElseThrow();
	}

	public List<MapTemplate> list() {
		return availableMaps;
	}

	public Optional<MapTemplate> get(String mapId) {
		if (mapId == null || mapId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(mapsById.get(mapId.trim()));
	}

	public MapTemplate defaultMap() {
		return defaultMap;
	}

	private static List<MapTemplate> loadMaps(
			ObjectMapper objectMapper,
			ResourcePatternResolver resourcePatternResolver,
			String resourcePattern
	) {
		List<MapTemplate> loadedMaps = new ArrayList<>();
		Set<String> seenIds = new HashSet<>();
		Resource[] resources = resolveResources(resourcePatternResolver, resourcePattern);
		Arrays.sort(resources, Comparator.comparing(MapTemplateRegistry::describeResource));

		for (Resource resource : resources) {
			try (InputStream inputStream = resource.getInputStream()) {
				MapManifest manifest = objectMapper.readValue(inputStream, MapManifest.class);
				if (Boolean.FALSE.equals(manifest.enabled())) {
					log.info("Skipping disabled map template {}", manifest.id());
					continue;
				}
				MapTemplate mapTemplate = toMapTemplate(objectMapper, resource, manifest);
				if (!seenIds.add(mapTemplate.id())) {
					throw new IllegalArgumentException("Duplicate map id: " + mapTemplate.id());
				}
				loadedMaps.add(mapTemplate);
			} catch (Exception exception) {
				log.warn(
						"Skipping invalid map template resource {}: {}",
						describeResource(resource),
						exception.getMessage()
				);
			}
		}

		return loadedMaps;
	}

	private static MapTemplate toMapTemplate(ObjectMapper objectMapper, Resource manifestResource, MapManifest manifest) throws IOException {
		MapTemplate.FileSet fileSet = new MapTemplate.FileSet(
				requireValue(manifest.files(), "Manifest files must not be null").colliders(),
				requireValue(manifest.files(), "Manifest files must not be null").spawnPoints(),
				requireValue(manifest.files(), "Manifest files must not be null").poi(),
				requireValue(manifest.files(), "Manifest files must not be null").minimap()
		);

		MapColliderFile colliderFile = readRelativeFile(objectMapper, manifestResource, fileSet.colliders(), MapColliderFile.class);
		MapSpawnPointsFile spawnPointsFile = readRelativeFile(objectMapper, manifestResource, fileSet.spawnPoints(), MapSpawnPointsFile.class);
		MapPoiFile poiFile = readRelativeFile(objectMapper, manifestResource, fileSet.poi(), MapPoiFile.class);
		MapMinimapFile minimapFile = readRelativeFile(objectMapper, manifestResource, fileSet.minimap(), MapMinimapFile.class);

		return new MapTemplate(
				manifest.id(),
				manifest.name(),
				manifest.region(),
				requireInt(manifest.revision(), "Manifest revision must not be null"),
				Boolean.TRUE.equals(manifest.defaultMap()),
				manifest.enabled() == null || manifest.enabled(),
				toBounds(manifest.bounds()),
				toSpawnRules(manifest.spawnRules()),
				fileSet,
				toPresentation(manifest.presentation()),
				toWind(manifest.defaultWind()),
				toColliders(colliderFile),
				toSpawnPoints(spawnPointsFile),
				toPointsOfInterest(poiFile),
				toMinimap(minimapFile)
		);
	}

	private static MapTemplate.Bounds toBounds(MapBoundsDefinition definition) {
		MapBoundsDefinition bounds = requireValue(definition, "Manifest bounds must not be null");
		return new MapTemplate.Bounds(
				requireDouble(bounds.minX(), "bounds.minX must not be null"),
				requireDouble(bounds.maxX(), "bounds.maxX must not be null"),
				requireDouble(bounds.minZ(), "bounds.minZ must not be null"),
				requireDouble(bounds.maxZ(), "bounds.maxZ must not be null")
		);
	}

	private static MapTemplate.SpawnRules toSpawnRules(MapSpawnRulesDefinition definition) {
		MapSpawnRulesDefinition spawnRules = requireValue(definition, "Manifest spawnRules must not be null");
		return new MapTemplate.SpawnRules(
				requireValue(spawnRules.playerSpawnMode(), "spawnRules.playerSpawnMode must not be null"),
				requireDouble(spawnRules.playerSpawnRadius(), "spawnRules.playerSpawnRadius must not be null"),
				Boolean.TRUE.equals(spawnRules.npcSpawnEnabled()),
				Boolean.TRUE.equals(spawnRules.resourceSpawnEnabled())
		);
	}

	private static MapTemplate.Presentation toPresentation(MapPresentationDefinition definition) {
		MapPresentationDefinition presentation = requireValue(definition, "Manifest presentation must not be null");
		return new MapTemplate.Presentation(presentation.theme(), presentation.previewImage());
	}

	private static MapTemplate.WindSettings toWind(MapWindDefinition definition) {
		MapWindDefinition defaultWind = requireValue(definition, "Manifest defaultWind must not be null");
		return new MapTemplate.WindSettings(
				requireDouble(defaultWind.angle(), "defaultWind.angle must not be null"),
				requireDouble(defaultWind.speed(), "defaultWind.speed must not be null")
		);
	}

	private static List<MapTemplate.Collider> toColliders(MapColliderFile colliderFile) {
		MapColliderFile file = requireValue(colliderFile, "Collider file must not be null");
		return requireValue(file.colliders(), "Collider list must not be null").stream()
				.map(collider -> new MapTemplate.Collider(
						collider.id(),
						collider.kind(),
						requireValue(collider.points(), "Collider points must not be null").stream()
								.map(point -> new MapTemplate.Point(
										requireDouble(point.x(), "Collider point x must not be null"),
										requireDouble(point.z(), "Collider point z must not be null")
								))
								.toList()
				))
				.toList();
	}

	private static List<MapTemplate.SpawnPoint> toSpawnPoints(MapSpawnPointsFile spawnPointsFile) {
		MapSpawnPointsFile file = requireValue(spawnPointsFile, "Spawn points file must not be null");
		return requireValue(file.playerSpawnPoints(), "Player spawn point list must not be null").stream()
				.map(spawnPoint -> new MapTemplate.SpawnPoint(
						spawnPoint.id(),
						requireDouble(spawnPoint.x(), "Spawn point x must not be null"),
						requireDouble(spawnPoint.z(), "Spawn point z must not be null"),
						requireDouble(spawnPoint.angle(), "Spawn point angle must not be null")
				))
				.toList();
	}

	private static List<MapTemplate.PointOfInterest> toPointsOfInterest(MapPoiFile poiFile) {
		MapPoiFile file = requireValue(poiFile, "POI file must not be null");
		return requireValue(file.pointsOfInterest(), "POI list must not be null").stream()
				.map(poi -> new MapTemplate.PointOfInterest(
						poi.id(),
						poi.name(),
						poi.kind(),
						requireDouble(poi.x(), "POI x must not be null"),
						requireDouble(poi.z(), "POI z must not be null")
				))
				.toList();
	}

	private static MapTemplate.Minimap toMinimap(MapMinimapFile minimapFile) {
		MapMinimapFile file = requireValue(minimapFile, "Minimap file must not be null");
		MapCalibrationDefinition calibration = requireValue(file.calibration(), "Minimap calibration must not be null");
		return new MapTemplate.Minimap(
				file.overlayImage(),
				new MapTemplate.Calibration(
						requireDouble(calibration.worldMinX(), "Minimap worldMinX must not be null"),
						requireDouble(calibration.worldMaxX(), "Minimap worldMaxX must not be null"),
						requireDouble(calibration.worldMinZ(), "Minimap worldMinZ must not be null"),
						requireDouble(calibration.worldMaxZ(), "Minimap worldMaxZ must not be null")
				)
		);
	}

	private static <T> T readRelativeFile(
			ObjectMapper objectMapper,
			Resource manifestResource,
			String relativePath,
			Class<T> type
	) throws IOException {
		Resource resource = manifestResource.createRelative(relativePath);
		if (resource == null || !resource.exists()) {
			throw new IllegalArgumentException("Missing map resource file: " + relativePath);
		}
		try (InputStream inputStream = resource.getInputStream()) {
			return objectMapper.readValue(inputStream, type);
		}
	}

	private static int requireInt(Integer value, String message) {
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private static double requireDouble(Double value, String message) {
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private static <T> T requireValue(T value, String message) {
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private static Resource[] resolveResources(
			ResourcePatternResolver resourcePatternResolver,
			String resourcePattern
	) {
		try {
			return resourcePatternResolver.getResources(resourcePattern);
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to load map template resources from " + resourcePattern, exception);
		}
	}

	private static String describeResource(Resource resource) {
		String description = resource.getDescription();
		return description == null || description.isBlank()
				? String.valueOf(resource.getFilename())
				: description;
	}

	private record MapManifest(
			String id,
			String name,
			String region,
			Integer revision,
			Boolean enabled,
			Boolean defaultMap,
			MapBoundsDefinition bounds,
			MapSpawnRulesDefinition spawnRules,
			MapFilesDefinition files,
			MapPresentationDefinition presentation,
			MapWindDefinition defaultWind
	) {
	}

	private record MapBoundsDefinition(Double minX, Double maxX, Double minZ, Double maxZ) {
	}

	private record MapSpawnRulesDefinition(
			MapTemplate.PlayerSpawnMode playerSpawnMode,
			Double playerSpawnRadius,
			Boolean npcSpawnEnabled,
			Boolean resourceSpawnEnabled
	) {
	}

	private record MapFilesDefinition(String colliders, String spawnPoints, String poi, String minimap) {
	}

	private record MapPresentationDefinition(String theme, String previewImage) {
	}

	private record MapWindDefinition(Double angle, Double speed) {
	}

	private record MapColliderFile(List<MapColliderDefinition> colliders) {
	}

	private record MapColliderDefinition(String id, String kind, List<MapPointDefinition> points) {
	}

	private record MapPointDefinition(Double x, Double z) {
	}

	private record MapSpawnPointsFile(List<MapSpawnPointDefinition> playerSpawnPoints) {
	}

	private record MapSpawnPointDefinition(String id, Double x, Double z, Double angle) {
	}

	private record MapPoiFile(List<MapPoiDefinition> pointsOfInterest) {
	}

	private record MapPoiDefinition(String id, String name, String kind, Double x, Double z) {
	}

	private record MapMinimapFile(String overlayImage, MapCalibrationDefinition calibration) {
	}

	private record MapCalibrationDefinition(Double worldMinX, Double worldMaxX, Double worldMinZ, Double worldMaxZ) {
	}
}