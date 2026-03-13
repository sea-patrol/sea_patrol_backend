package ru.sea.patrol.game.physics;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.sea.patrol.service.game.Player;
import ru.sea.patrol.service.game.PlayerShipInstance;
import ru.sea.patrol.service.game.Wind;
import ru.sea.patrol.ws.protocol.dto.PlayerInputMessage;

class PlayerShipInstancePhysicsTest extends Box2DTestBase {

	private static final float TIME_STEP = 1f / 60f;
	private static final int VELOCITY_ITERS = 6;
	private static final int POSITION_ITERS = 2;
	private static final Offset<Float> FLOAT_EPS = Offset.offset(0.01f);

	private PlayerShipInstance ship;

	@AfterEach
	void disposeShip() {
		if (ship != null) {
			ship.dispose();
			ship = null;
		}
	}

	@Test
	void defaultFullSails_accelerateShipUnderWindWithoutThrottleInput() {
		ship = createShip(3);

		float initialSpeed = ship.getVelocity();
		stepWithUpdate(new Wind(0f, 10f), 120);

		assertThat(initialSpeed).isGreaterThanOrEqualTo(0f);
		assertThat(ship.getSailLevel()).isEqualTo(3);
		assertThat(ship.getVelocity()).isGreaterThan(0.1f);
	}

	@Test
	void sailLevelZero_preventsWindDrivenAcceleration() {
		ship = createShip(0);

		stepWithUpdate(new Wind(0f, 10f), 120);

		assertThat(ship.getSailLevel()).isEqualTo(0);
		assertThat(ship.getVelocity()).isCloseTo(0f, FLOAT_EPS);
	}

	@Test
	void sailLevelControlsUseRisingEdgeAndClampBetweenZeroAndThree() {
		Player player = createPlayer(3);
		ship = new PlayerShipInstance(world, player);

		ship.setInput(new PlayerInputMessage(false, false, false, true));
		assertThat(ship.getSailLevel()).isEqualTo(2);
		assertThat(player.getSailLevel()).isEqualTo(2);

		ship.setInput(new PlayerInputMessage(false, false, false, true));
		assertThat(ship.getSailLevel()).isEqualTo(2);

		ship.setInput(new PlayerInputMessage(false, false, false, false));
		ship.setInput(new PlayerInputMessage(false, false, false, true));
		assertThat(ship.getSailLevel()).isEqualTo(1);

		ship.setInput(new PlayerInputMessage(false, false, false, false));
		ship.setInput(new PlayerInputMessage(false, false, false, true));
		ship.setInput(new PlayerInputMessage(false, false, false, false));
		ship.setInput(new PlayerInputMessage(false, false, false, true));
		assertThat(ship.getSailLevel()).isEqualTo(0);

		ship.setInput(new PlayerInputMessage(false, false, false, false));
		ship.setInput(new PlayerInputMessage(false, false, true, false));
		ship.setInput(new PlayerInputMessage(false, false, false, false));
		ship.setInput(new PlayerInputMessage(false, false, true, false));
		ship.setInput(new PlayerInputMessage(false, false, false, false));
		ship.setInput(new PlayerInputMessage(false, false, true, false));
		ship.setInput(new PlayerInputMessage(false, false, false, false));
		ship.setInput(new PlayerInputMessage(false, false, true, false));
		assertThat(ship.getSailLevel()).isEqualTo(3);
		assertThat(player.getSailLevel()).isEqualTo(3);
	}

	@Test
	void higherSailLevels_produceHigherSpeedForSameWind() {
		float lowSailsSpeed = accelerateWithWind(1, new Wind(0f, 10f));
		float mediumSailsSpeed = accelerateWithWind(2, new Wind(0f, 10f));
		float fullSailsSpeed = accelerateWithWind(3, new Wind(0f, 10f));

		assertThat(mediumSailsSpeed).isGreaterThan(lowSailsSpeed);
		assertThat(fullSailsSpeed).isGreaterThan(mediumSailsSpeed);
	}

	@Test
	void sailingSpeed_differsForTailwindBeamAndHeadwind() {
		float tailwindSpeed = accelerateWithWind(3, new Wind(0f, 10f));
		float beamReachSpeed = accelerateWithWind(3, new Wind((float) (Math.PI / 2), 10f));
		float headwindSpeed = accelerateWithWind(3, new Wind((float) Math.PI, 10f));

		assertThat(beamReachSpeed).isGreaterThan(tailwindSpeed);
		assertThat(tailwindSpeed).isGreaterThan(headwindSpeed);
		assertThat(headwindSpeed).isGreaterThan(0.05f);
	}

	@Test
	void turnLeftAndRight_stillChangeOrientation() {
		ship = createShip(0);
		ship.setInput(new PlayerInputMessage(true, false, false, false));
		float initialAngle = ship.getOrientation();
		stepWithUpdate(new Wind(0f, 10f), 120);
		float leftTurnAngle = ship.getOrientation();

		ship.setInput(new PlayerInputMessage(false, true, false, false));
		stepWithUpdate(new Wind(0f, 10f), 120);
		float rightTurnAngle = ship.getOrientation();

		assertThat(leftTurnAngle).isGreaterThan(initialAngle + 0.01f);
		assertThat(rightTurnAngle).isLessThan(leftTurnAngle - 0.01f);
	}

	private void stepWithUpdate(Wind wind, int steps) {
		for (int i = 0; i < steps; i++) {
			ship.update(TIME_STEP, wind);
			world.step(TIME_STEP, VELOCITY_ITERS, POSITION_ITERS);
		}
	}

	private float accelerateWithWind(int sailLevel, Wind wind) {
		ship = createShip(sailLevel);
		stepWithUpdate(wind, 120);

		float speed = ship.getVelocity();
		ship.dispose();
		ship = null;
		return speed;
	}

	private PlayerShipInstance createShip(int sailLevel) {
		return new PlayerShipInstance(world, createPlayer(sailLevel));
	}

	private Player createPlayer(int sailLevel) {
		return new Player("test")
				.setX(0)
				.setZ(0)
				.setAngle(0)
				.setWidth(7f)
				.setLength(26f)
				.setSailLevel(sailLevel);
	}
}
