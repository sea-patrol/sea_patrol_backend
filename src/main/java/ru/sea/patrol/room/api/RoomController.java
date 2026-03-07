package ru.sea.patrol.room.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.sea.patrol.room.api.dto.RoomCatalogResponseDto;
import ru.sea.patrol.room.api.dto.RoomCreateRequestDto;
import ru.sea.patrol.room.api.dto.RoomSummaryDto;
import ru.sea.patrol.service.game.RoomCatalogService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rooms")
public class RoomController {

	private final RoomCatalogService roomCatalogService;

	@GetMapping
	public Mono<RoomCatalogResponseDto> listRooms() {
		return Mono.fromSupplier(roomCatalogService::getCatalog);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Mono<RoomSummaryDto> createRoom(@RequestBody RoomCreateRequestDto request) {
		return Mono.fromSupplier(() -> roomCatalogService.createRoom(request));
	}
}
