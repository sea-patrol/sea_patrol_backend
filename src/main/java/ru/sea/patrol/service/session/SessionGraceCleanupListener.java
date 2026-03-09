package ru.sea.patrol.service.session;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.sea.patrol.service.game.GameService;
import ru.sea.patrol.service.game.RoomCatalogWsService;

@Component
@RequiredArgsConstructor
public class SessionGraceCleanupListener {

	private final GameService gameService;
	private final RoomCatalogWsService roomCatalogWsService;

	@EventListener
	public void onGraceExpired(SessionGraceExpiredEvent event) {
		boolean roomCatalogChanged = gameService.cleanupPlayer(event.username());
		if (roomCatalogChanged) {
			roomCatalogWsService.publishRoomsUpdated();
		}
	}
}
