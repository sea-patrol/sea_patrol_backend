package ru.sea.patrol.game.physics;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.math.Vector2;

final class Box2DTestSupport {

	private Box2DTestSupport() {
	}

	static void assertApprox(float actual, float expected, float eps) {
		assertThat(actual).isCloseTo(expected, org.assertj.core.data.Offset.offset(eps));
	}

	static void assertApprox(Vector2 actual, Vector2 expected, float eps) {
		assertApprox(actual.x, expected.x, eps);
		assertApprox(actual.y, expected.y, eps);
	}
}

