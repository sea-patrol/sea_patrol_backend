package ru.sea.patrol.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.sea.patrol.service.game.Wind;

class WindTest {

	@Test
	void update_rotatesDirectionClockwiseByConfiguredSpeed() {
		Wind wind = new Wind((float) (Math.PI / 2), 10.0f, (float) (Math.PI / 2));

		wind.update(1.0f);

		assertThat(wind.getAngleRad()).isCloseTo(0.0f, org.assertj.core.data.Offset.offset(0.0001f));
		assertThat(wind.getSpeed()).isEqualTo(10.0f);
	}

	@Test
	void update_wrapsAngleIntoZeroToTwoPiRange() {
		Wind wind = new Wind(0.1f, 10.0f, 1.0f);

		wind.update(0.2f);

		assertThat(wind.getAngleRad()).isGreaterThan((float) (Math.PI * 2.0 - 0.15));
		assertThat(wind.getAngleRad()).isLessThan((float) (Math.PI * 2.0));
	}

	@Test
	void update_withZeroRotationSpeed_keepsDirectionStable() {
		Wind wind = new Wind((float) (Math.PI / 3), 8.0f, 0.0f);

		wind.update(5.0f);

		assertThat(wind.getAngleRad()).isCloseTo((float) (Math.PI / 3), org.assertj.core.data.Offset.offset(0.0001f));
	}
}
