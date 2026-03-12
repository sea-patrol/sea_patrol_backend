package ru.sea.patrol.service.game;

import com.badlogic.gdx.math.Vector2;
import java.util.Random;
import lombok.Getter;

public class Wind {

	@Getter
	private final Vector2 direction;

	@Getter
	private float speed;

	private final Random rand = new Random();

	public Wind() {
		this(0.0f, 10.0f);
	}

	public Wind(float angleRad, float speed) {
		this.direction = new Vector2((float) Math.cos(angleRad), (float) Math.sin(angleRad));
		this.speed = speed;
	}

	public void update(float delta) {
		float change = (rand.nextFloat() - 0.5f) * 0.5f * delta;
		float angle = direction.angleRad() + change;
		direction.set((float) Math.cos(angle), (float) Math.sin(angle));
	}
}
