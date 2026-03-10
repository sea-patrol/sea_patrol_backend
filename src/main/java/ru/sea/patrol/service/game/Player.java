package ru.sea.patrol.service.game;

import com.badlogic.gdx.physics.box2d.World;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import ru.sea.patrol.ws.protocol.dto.MessageOutput;

@Getter
@Setter
@Accessors(chain = true)
public class Player {

	private int health;
	private int maxHealth;
	private float velocity;
	private float x;
	private float z;
	private float angle;
	private String model;
	private float height;
	private float width;
	private float length;

	private GameRoom room;
	private Disposable roomSubscription;

	private final String name;
	private Sinks.Many<MessageOutput> sink;

	private PlayerShipInstance ship;

	public Player(String name) {
		this.name = name;
		this.sink = newSessionSink();
	}

	public void resetSessionSink() {
		sink = newSessionSink();
	}

	public void reply(MessageOutput message) {
		if (message != null) {
			sink.tryEmitNext(message);
		}
	}

	public void joinRoom(GameRoom room) {
		joinRoom(room, true);
	}

	public void joinRoom(GameRoom room, boolean activateSubscription) {
		if (this.room != null) {
			this.room.leave(this.name);
		}
		this.room = room;
		if (activateSubscription) {
			activateRoomSubscription();
		}
	}

	public void activateRoomSubscription() {
		if (room == null) {
			return;
		}
		if (roomSubscription != null) {
			roomSubscription.dispose();
		}
		roomSubscription = room.getSink().asFlux().subscribe(this::reply);
		if (room.isStarted() && room.getWorld() != null && ship == null) {
			createShipInstanceInGameWorld(room.getWorld());
		}
	}

	public void deactivateRoomSubscription() {
		if (roomSubscription != null) {
			roomSubscription.dispose();
			roomSubscription = null;
		}
	}

	public void prepareForDisconnectGrace() {
		deactivateRoomSubscription();
		if (ship != null) {
			setX(ship.getFrontendX());
			setZ(ship.getFrontendZ());
			setAngle(ship.getOrientation());
			ship.freeze();
			setVelocity(0f);
		}
	}

	public void createShipInstanceInGameWorld(World world) {
		this.ship = new PlayerShipInstance(world, this);
	}

	public void leaveRoom() {
		this.room = null;
		deactivateRoomSubscription();
		if (this.ship != null) {
			this.ship.dispose();
			this.ship = null;
		}
	}

	private static Sinks.Many<MessageOutput> newSessionSink() {
		return Sinks.many().replay().limit(16);
	}
}

