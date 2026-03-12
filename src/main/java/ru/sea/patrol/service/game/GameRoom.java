package ru.sea.patrol.service.game;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitResult;
import ru.sea.patrol.service.game.map.MapTemplate;
import ru.sea.patrol.ws.protocol.MessageType;
import ru.sea.patrol.ws.protocol.dto.InitGameStateMessage;
import ru.sea.patrol.ws.protocol.dto.MessageOutput;
import ru.sea.patrol.ws.protocol.dto.PlayerInfo;
import ru.sea.patrol.ws.protocol.dto.PlayerUpdateInfo;
import ru.sea.patrol.ws.protocol.dto.RoomStateInfo;
import ru.sea.patrol.ws.protocol.dto.UpdateGameStateMessage;
import ru.sea.patrol.ws.protocol.dto.WindInfo;

@Slf4j
public class GameRoom {

	@Getter
	private final String name;

	@Getter
	private final String roomName;

	@Getter
	private final MapTemplate mapTemplate;

	private final Map<String, Player> players = new ConcurrentHashMap<>();

	@Getter
	private final Sinks.Many<MessageOutput> sink;

	@Getter
	private World world;

	private Wind wind;
	private long lastTime = System.nanoTime();
	private float delta;

	private ScheduledExecutorService scheduler;
	private ScheduledFuture<?> scheduledFuture;

	@Getter
	private volatile boolean started = false;

	@Getter
	private final long updatePeriodMillis;

	public GameRoom(String roomId, long updatePeriodMillis) {
		this(roomId, roomId, MapTemplate.mvpDefault(), updatePeriodMillis);
	}

	public GameRoom(String roomId, String roomName, MapTemplate mapTemplate, long updatePeriodMillis) {
		if (updatePeriodMillis <= 0) {
			throw new IllegalArgumentException("updatePeriodMillis must be greater than zero");
		}
		this.name = roomId;
		this.roomName = roomName == null || roomName.isBlank() ? roomId : roomName.trim();
		this.mapTemplate = mapTemplate == null ? MapTemplate.mvpDefault() : mapTemplate;
		this.updatePeriodMillis = updatePeriodMillis;
		this.sink = Sinks.many().multicast().directBestEffort();
	}

	public synchronized void join(Player player) {
		join(player, true);
	}

	public synchronized void join(Player player, boolean activateImmediately) {
		log.info("Player {} joining room {}", player.getName(), name);
		players.put(player.getName(), player);
		player.joinRoom(this, false);
		if (activateImmediately) {
			activateJoinedPlayer(player);
		}
	}

	public synchronized void activateJoinedPlayer(Player player) {
		if (player == null) {
			return;
		}
		if (started) {
			broadcastPlayerJoinMessage(player);
			player.activateRoomSubscription();
			sendStartMessage(player);
			return;
		}
		player.activateRoomSubscription();
	}

	public synchronized void resumePlayer(Player player) {
		if (player == null || !players.containsKey(player.getName())) {
			return;
		}
		player.activateRoomSubscription();
		if (started) {
			sendStartMessage(player);
			return;
		}
		start();
	}

	public synchronized void leave(String playerName) {
		log.info("Player {} leaving room {}", playerName, name);
		var player = players.remove(playerName);
		if (player != null) {
			player.leaveRoom();
			if (started) {
				broadcastPlayerLeaveMessage(player);
				if (players.isEmpty()) {
					stop();
				}
			}
		}
	}

	public boolean isEmpty() {
		return players.isEmpty();
	}

	public int getPlayerCount() {
		return players.size();
	}

	public synchronized void start() {
		start(true);
	}

	public synchronized void start(boolean scheduleUpdates) {
		log.info("Starting room game {}", name);
		if (started) {
			return;
		}

		world = new World(new Vector2(0, 0), true);
		wind = new Wind((float) mapTemplate.defaultWind().angle(), (float) mapTemplate.defaultWind().speed());
		for (var player : players.values()) {
			player.createShipInstanceInGameWorld(world);
		}

		lastTime = System.nanoTime();
		delta = retrieveDelta();
		started = true;
		players.values().forEach(this::sendStartMessage);

		if (scheduleUpdates) {
			startSchedulerIfNeeded();
		}
	}

	public synchronized void update() {
		if (!started || world == null || wind == null) {
			return;
		}

		delta = retrieveDelta();
		wind.update(delta);
		players.values().forEach(player -> {
			if (player.getShip() != null) {
				player.getShip().update(delta, wind);
			}
		});

		world.step(delta, 6, 2);
		sendUpdateMessage();
	}

	public synchronized void stop() {
		if (!started && scheduler == null && world == null && wind == null) {
			return;
		}

		log.info("Stopping room game {}", name);
		if (scheduledFuture != null) {
			scheduledFuture.cancel(false);
			scheduledFuture = null;
		}
		if (scheduler != null) {
			scheduler.shutdownNow();
			scheduler = null;
		}

		for (var player : players.values()) {
			player.leaveRoom();
		}

		if (world != null) {
			world.dispose();
			world = null;
		}
		wind = null;
		started = false;
	}

	private void startSchedulerIfNeeded() {
		if (scheduler != null) {
			return;
		}
		scheduler = Executors.newSingleThreadScheduledExecutor(roomThreadFactory());
		scheduledFuture = scheduler.scheduleWithFixedDelay(this::update, 0, updatePeriodMillis, TimeUnit.MILLISECONDS);
	}

	private ThreadFactory roomThreadFactory() {
		return runnable -> {
			Thread t = new Thread(runnable, "sea-patrol-room-" + name);
			t.setDaemon(true);
			t.setUncaughtExceptionHandler((thread, ex) -> log.error("Uncaught exception in {}", thread.getName(), ex));
			return t;
		};
	}

	private float retrieveDelta() {
		long currentTime = System.nanoTime();
		float newDelta = (currentTime - lastTime) / 1_000_000_000.0f;
		lastTime = currentTime;
		return newDelta;
	}

	public void sendStartMessage(Player player) {
		var startMessage = new MessageOutput(
				MessageType.INIT_GAME_STATE,
				new InitGameStateMessage(
						name,
						RoomStateInfo.from(name, roomName, mapTemplate),
						toWindInfo(),
						players.values().stream()
								.map(p -> new PlayerInfo(
										p.getName(),
										p.getHealth(),
										p.getMaxHealth(),
										p.getShip().getVelocity(),
										p.getShip().getFrontendX(),
										p.getShip().getFrontendZ(),
										p.getShip().getOrientation(),
										p.getModel(),
										p.getHeight(),
										p.getWidth(),
										p.getLength()
								))
								.collect(Collectors.toList())
				)
		);

		player.getSink().tryEmitNext(startMessage);
		log.info("Room game {} sent start message for player {}", name, player.getName());
	}

	private void sendUpdateMessage() {
		var updateMessage = new MessageOutput(
				MessageType.UPDATE_GAME_STATE,
				new UpdateGameStateMessage(
					delta,
					toWindInfo(),
					players.values().stream()
							.map(player -> new PlayerUpdateInfo(
									player.getName(),
									player.getHealth(),
									player.getShip().getVelocity(),
									player.getShip().getFrontendX(),
									player.getShip().getFrontendZ(),
									player.getShip().getOrientation()
							))
							.collect(Collectors.toList())
				)
		);

		EmitResult result = sink.tryEmitNext(updateMessage);
		if (result.isFailure() && result != EmitResult.FAIL_ZERO_SUBSCRIBER) {
			log.debug("Room {} dropped UPDATE_GAME_STATE due to {}", name, result);
		}
	}

	public void broadcastPlayerJoinMessage(Player player) {
		var joinMessage = new MessageOutput(
				MessageType.PLAYER_JOIN,
				new PlayerInfo(
						player.getName(),
						player.getHealth(),
						player.getMaxHealth(),
						player.getShip() == null ? 0.0f : player.getShip().getVelocity(),
						player.getShip() == null ? player.getX() : player.getShip().getFrontendX(),
						player.getShip() == null ? player.getZ() : player.getShip().getFrontendZ(),
						player.getShip() == null ? player.getAngle() : player.getShip().getOrientation(),
						player.getModel(),
						player.getHeight(),
						player.getWidth(),
						player.getLength()
				)
		);
		sink.tryEmitNext(joinMessage);
		log.info("Player {} joined in the room game {}", player.getName(), name);
	}

	private void broadcastPlayerLeaveMessage(Player player) {
		var leaveMessage = new MessageOutput(MessageType.PLAYER_LEAVE, player.getName());
		sink.tryEmitNext(leaveMessage);
		log.info("Player {} left the room game {}", player.getName(), name);
	}

	private WindInfo toWindInfo() {
		return new WindInfo(wind.getDirection().angleRad(), wind.getSpeed());
	}
}
