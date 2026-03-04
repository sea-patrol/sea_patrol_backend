package ru.sea.patrol.game.physics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.sea.patrol.dto.websocket.PlayerInputMessage;
import ru.sea.patrol.service.game.Player;
import ru.sea.patrol.service.game.PlayerShipInstance;
import ru.sea.patrol.service.game.Wind;

class PlayerShipInstancePhysicsTest extends Box2DTestBase {

	private static final float TIME_STEP = 1f / 60f;
	private static final int VELOCITY_ITERS = 6;
	private static final int POSITION_ITERS = 2;

	private final Wind wind = new Wind();

	private PlayerShipInstance ship;

	@AfterEach
	void disposeShip() {
		if (ship != null) {
			ship.dispose();
			ship = null;
		}
	}

	@Test
	void thrust_increasesSpeed() {
		ship = createShip();
		ship.setInput(new PlayerInputMessage(false, false, true, false));

		float initialSpeed = ship.getVelocity();
		stepWithUpdate(120);
		float finalSpeed = ship.getVelocity();

		assertThat(initialSpeed).isGreaterThanOrEqualTo(0f);
		assertThat(finalSpeed).isGreaterThan(0.1f);
		assertThat(finalSpeed).isGreaterThan(initialSpeed);
	}

	@Test
	void turnLeft_changesOrientationPositive() {
		ship = createShip();
		ship.setInput(new PlayerInputMessage(true, false, false, false));

		float initialAngle = ship.getOrientation();
		stepWithUpdate(120);
		float finalAngle = ship.getOrientation();

		assertThat(finalAngle).isGreaterThan(initialAngle + 0.01f);
	}

	@Test
	void turnRight_changesOrientationNegative() {
		ship = createShip();
		ship.setInput(new PlayerInputMessage(false, true, false, false));

		float initialAngle = ship.getOrientation();
		stepWithUpdate(120);
		float finalAngle = ship.getOrientation();

		assertThat(finalAngle).isLessThan(initialAngle - 0.01f);
	}

	@Test
	void damping_reducesSpeed_whenNoThrust() {
		ship = createShip();
		ship.setInput(new PlayerInputMessage(false, false, true, false));
		stepWithUpdate(60);
		float acceleratedSpeed = ship.getVelocity();

		ship.setInput(new PlayerInputMessage(false, false, false, false));
		stepWithUpdate(240);
		float dampedSpeed = ship.getVelocity();

		assertThat(acceleratedSpeed).isGreaterThan(0.1f);
		assertThat(dampedSpeed).isLessThan(acceleratedSpeed);
		assertThat(dampedSpeed).isLessThan(acceleratedSpeed * 0.75f);
	}

	@Test
	void nullInput_doesNotChangeVelocityOrOrientation() {
		ship = createShip();
		ship.setInput(null);

		stepWithUpdate(120);

		assertThat(ship.getVelocity()).isCloseTo(0f, org.assertj.core.data.Offset.offset(EPS));
		assertThat(ship.getOrientation()).isCloseTo(0f, org.assertj.core.data.Offset.offset(EPS));
	}

	private void stepWithUpdate(int steps) {
		for (int i = 0; i < steps; i++) {
			ship.update(TIME_STEP, wind);
			world.step(TIME_STEP, VELOCITY_ITERS, POSITION_ITERS);
		}
	}

	private PlayerShipInstance createShip() {
		Player player = new Player("test")
				.setX(0)
				.setZ(0)
				.setAngle(0)
				.setWidth(7f)
				.setLength(26f);
		return new PlayerShipInstance(world, player);
	}
}

