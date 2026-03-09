package ru.sea.patrol.service.game;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitResult;
import ru.sea.patrol.ws.protocol.MessageType;
import ru.sea.patrol.ws.protocol.dto.MessageOutput;

@Service
public class RoomCatalogWsService {

	private final RoomCatalogService roomCatalogService;
	private final Sinks.Many<MessageOutput> updatesSink = Sinks.many().multicast().directBestEffort();

	public RoomCatalogWsService(RoomCatalogService roomCatalogService) {
		this.roomCatalogService = roomCatalogService;
	}

	public Flux<MessageOutput> initialize() {
		return updatesSink.asFlux();
	}

	public MessageOutput snapshotMessage() {
		return new MessageOutput(MessageType.ROOMS_SNAPSHOT, roomCatalogService.getCatalog());
	}

	public void publishRoomsUpdated() {
		EmitResult result = updatesSink.tryEmitNext(new MessageOutput(MessageType.ROOMS_UPDATED, roomCatalogService.getCatalog()));
		if (result.isFailure() && result != EmitResult.FAIL_ZERO_SUBSCRIBER) {
			throw new IllegalStateException("Failed to emit ROOMS_UPDATED: " + result);
		}
	}
}
