package ru.sea.patrol.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.sea.patrol.error.domain.UnauthorizedException;
import ru.sea.patrol.user.domain.UserEntity;
import ru.sea.patrol.user.domain.UserRole;

@SpringBootTest
class JwtUtilTest {

	@Autowired
	private JwtUtil jwtUtil;

	@Test
	void generateToken_thenCheck_returnsClaims() {
		UserEntity user = UserEntity.builder()
				.username("alice")
				.role(UserRole.USER)
				.build();

		String token = jwtUtil.generateToken(user).getToken();
		TokenVerificationResult result = jwtUtil.check(token).block();

		assertThat(result).isNotNull();
		assertThat(result.getClaims().getSubject()).isEqualTo("alice");
		assertThat(result.getClaims().get("role", String.class)).isEqualTo("USER");
	}

	@Test
	void check_expiredToken_returnsUnauthorizedException() {
		String token = jwtUtil.generateToken(
				new Date(System.currentTimeMillis() - 5_000),
				new HashMap<>(Map.of("role", "USER")),
				"alice"
		).getToken();

		assertThatThrownBy(() -> jwtUtil.check(token).block())
				.satisfies(JwtUtilTest::assertUnauthorized);
	}

	@Test
	void check_invalidToken_returnsUnauthorizedException() {
		assertThatThrownBy(() -> jwtUtil.check("not-a-jwt").block())
				.satisfies(JwtUtilTest::assertUnauthorized);
	}

	private static void assertUnauthorized(Throwable thrown) {
		if (thrown instanceof UnauthorizedException) {
			return;
		}
		Throwable cause = thrown.getCause();
		while (cause != null) {
			if (cause instanceof UnauthorizedException) {
				return;
			}
			cause = cause.getCause();
		}
		throw new AssertionError("Expected UnauthorizedException, but got: " + thrown.getClass().getName(), thrown);
	}
}
