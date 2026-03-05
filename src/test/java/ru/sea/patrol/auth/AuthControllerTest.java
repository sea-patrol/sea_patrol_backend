package ru.sea.patrol.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.sea.patrol.auth.api.dto.AuthResponseDto;

@SpringBootTest
@AutoConfigureWebTestClient
class AuthControllerTest {

	@Autowired
	private WebTestClient webTestClient;

	private String loginAndGetToken(String username, String password) {
		AuthResponseDto response = webTestClient
				.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "username": "%s",
						  "password": "%s"
						}
						""".formatted(username, password))
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody(AuthResponseDto.class)
				.returnResult()
				.getResponseBody();

		assertThat(response).isNotNull();
		assertThat(response.getToken()).isNotBlank();
		return response.getToken();
	}

	@Test
	void signup_createsUser_andReturnsUsername() {
		String username = "test_" + UUID.randomUUID();

		webTestClient
				.post()
				.uri("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "username": "%s",
						  "password": "123456",
						  "email": "user@example.com"
						}
						""".formatted(username))
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.username").isEqualTo(username);
	}

	@Test
	void login_returnsToken_andTimestamps_forExistingUser() {
		webTestClient
				.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "username": "user1",
						  "password": "123456"
						}
						""")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.consumeWith(result -> assertThat(result.getResponseBody()).isNotNull())
				.jsonPath("$.username").isEqualTo("user1")
				.jsonPath("$.token").isNotEmpty()
				.jsonPath("$.issuedAt").isNotEmpty()
				.jsonPath("$.expiresAt").isNotEmpty();
	}

	@Test
	void me_withoutToken_returns401() {
		webTestClient
				.get()
				.uri("/api/v1/auth/me")
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void me_withEmptyBearerToken_returns401_withErrorCode() {
		webTestClient
				.get()
				.uri("/api/v1/auth/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer ")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.errors[0].code").isEqualTo("SEAPATROL_UNAUTHORIZED");
	}

	@Test
	void me_withGarbageToken_returns401_withErrorCode() {
		webTestClient
				.get()
				.uri("/api/v1/auth/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.errors[0].code").isEqualTo("SEAPATROL_UNAUTHORIZED");
	}

	@Test
	void me_withToken_returnsUsername() {
		String token = loginAndGetToken("user1", "123456");

		webTestClient
				.get()
				.uri("/api/v1/auth/me")
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.username").isEqualTo("user1");
	}

	@Test
	void signup_thenLogin_works() {
		String username = "test_" + UUID.randomUUID();

		webTestClient
				.post()
				.uri("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "username": "%s",
						  "password": "123456",
						  "email": "user@example.com"
						}
						""".formatted(username))
				.exchange()
				.expectStatus().isOk();

		webTestClient
				.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "username": "%s",
						  "password": "123456"
						}
						""".formatted(username))
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.username").isEqualTo(username)
				.jsonPath("$.token").isNotEmpty();
	}

	@Test
	void login_invalidPassword_returns401_withErrorCode() {
		webTestClient
				.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "username": "user1",
						  "password": "wrong"
						}
						""")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.errors[0].code").isEqualTo("SEAPATROL_INVALID_PASSWORD");
	}

	@Test
	void login_invalidUsername_returns401_withErrorCode() {
		webTestClient
				.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "username": "no_such_user",
						  "password": "123456"
						}
						""")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.errors[0].code").isEqualTo("SEAPATROL_INVALID_USERNAME");
	}

	@Test
	void signup_invalidEmail_returns400_withValidationError() {
		webTestClient
				.post()
				.uri("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "username": "user_ok",
						  "password": "123456",
						  "email": "not-an-email"
						}
						""")
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.errors[0].code").isEqualTo("SEAPATROL_VALIDATION_ERROR");
	}

	@Test
	void login_blankFields_returns400_withValidationError() {
		webTestClient
				.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "username": "",
						  "password": ""
						}
						""")
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.errors[0].code").isEqualTo("SEAPATROL_VALIDATION_ERROR");
	}

}
