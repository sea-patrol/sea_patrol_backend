package ru.sea.patrol.service.game;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmptyRoomCleanupListener {

	private final RoomCatalogWsService roomCatalogWsService;

	@EventListener
	public void onEmptyRoomExpired(EmptyRoomExpiredEvent event) {
		roomCatalogWsService.publishRoomsUpdated();
	}
}
