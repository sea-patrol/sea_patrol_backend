package ru.sea.patrol.service.game;

import com.badlogic.gdx.math.Vector2;
import lombok.Getter;

public class Wind {

	public static final float DEFAULT_ROTATION_SPEED_RAD_PER_SECOND = (float) Math.toRadians(10.0);
	private static final float FULL_CIRCLE_RAD = (float) (Math.PI * 2.0);

	@Getter
	private final Vector2 direction;

	@Getter
	private float speed;

	@Getter
	private final float rotationSpeedRadPerSecond;

	public Wind() {
		this(0.0f, 10.0f, DEFAULT_ROTATION_SPEED_RAD_PER_SECOND);
	}

	public Wind(float angleRad, float speed) {
		this(angleRad, speed, DEFAULT_ROTATION_SPEED_RAD_PER_SECOND);
	}

	public Wind(float angleRad, float speed, float rotationSpeedRadPerSecond) {
		this.direction = new Vector2();
		this.speed = speed;
		this.rotationSpeedRadPerSecond = Math.max(0.0f, rotationSpeedRadPerSecond);
		setAngleRad(angleRad);
	}

	public void update(float delta) {
		if (delta <= 0.0f || rotationSpeedRadPerSecond <= 0.0f) {
			return;
		}
		setAngleRad(getAngleRad() - rotationSpeedRadPerSecond * delta);
	}

	public float getAngleRad() {
		return normalizeAngle(direction.angleRad());
	}

	private void setAngleRad(float angleRad) {
		float normalizedAngle = normalizeAngle(angleRad);
		direction.set((float) Math.cos(normalizedAngle), (float) Math.sin(normalizedAngle));
	}

	private static float normalizeAngle(float angleRad) {
		float normalized = angleRad % FULL_CIRCLE_RAD;
		if (normalized < 0.0f) {
			normalized += FULL_CIRCLE_RAD;
		}
		return normalized;
	}
}
