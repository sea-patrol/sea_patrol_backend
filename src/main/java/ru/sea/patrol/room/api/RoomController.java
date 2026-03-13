package ru.sea.patrol.room.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.sea.patrol.room.api.dto.RoomCatalogResponseDto;
import ru.sea.patrol.room.api.dto.RoomCreateRequestDto;
import ru.sea.patrol.room.api.dto.RoomLeaveResponseDto;
import ru.sea.patrol.room.api.dto.RoomSummaryDto;
import ru.sea.patrol.service.game.RoomCatalogService;
import ru.sea.patrol.service.game.RoomCatalogWsService;
import ru.sea.patrol.service.game.RoomJoinService;
import ru.sea.patrol.service.game.RoomLeaveService;
import ru.sea.patrol.ws.protocol.dto.RoomJoinResponseDto;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rooms")
public class RoomController {

	private final RoomCatalogService roomCatalogService;
	private final RoomCatalogWsService roomCatalogWsService;
	private final RoomJoinService roomJoinService;
	private final RoomLeaveService roomLeaveService;

	@GetMapping
	public Mono<RoomCatalogResponseDto> listRooms() {
		return Mono.fromSupplier(roomCatalogService::getCatalog);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Mono<RoomSummaryDto> createRoom(@RequestBody RoomCreateRequestDto request) {
		return Mono.fromSupplier(() -> {
			RoomSummaryDto createdRoom = roomCatalogService.createRoom(request);
			roomCatalogWsService.publishRoomsUpdated();
			return createdRoom;
		});
	}

	@PostMapping("/{roomId}/join")
	public Mono<RoomJoinResponseDto> joinRoom(@PathVariable String roomId) {
		return ReactiveSecurityContextHolder.getContext()
				.map(SecurityContext::getAuthentication)
				.map(authentication -> authentication.getName())
				.map(username -> roomJoinService.joinRoom(username, roomId));
	}

	@PostMapping("/{roomId}/leave")
	public Mono<RoomLeaveResponseDto> leaveRoom(@PathVariable String roomId) {
		return ReactiveSecurityContextHolder.getContext()
				.map(SecurityContext::getAuthentication)
				.map(authentication -> authentication.getName())
				.map(username -> roomLeaveService.leaveRoom(username, roomId));
	}
}
