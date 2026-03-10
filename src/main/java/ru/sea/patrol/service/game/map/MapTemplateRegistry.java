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
				MapTemplate mapTemplate = new MapTemplate(
						manifest.id(),
						manifest.name(),
						Boolean.TRUE.equals(manifest.defaultMap())
				);
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

	private record MapManifest(String id, String name, Boolean defaultMap) {
	}
}