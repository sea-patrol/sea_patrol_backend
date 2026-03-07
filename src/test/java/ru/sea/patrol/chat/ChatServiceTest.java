package ru.sea.patrol.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import ru.sea.patrol.service.chat.ChatService;
import ru.sea.patrol.service.session.GameSessionRegistry;
import ru.sea.patrol.ws.protocol.MessageType;
import ru.sea.patrol.ws.protocol.dto.ChatMessage;
import ru.sea.patrol.ws.protocol.dto.MessageOutput;
import tools.jackson.databind.ObjectMapper;

class ChatServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void lobbyScopedMessage_isDeliveredOnlyToLobbyUsers_andLegacyGlobalTargetIsRewritten() {
		GameSessionRegistry sessionRegistry = mock(GameSessionRegistry.class);
		ChatService chatService = new ChatService(objectMapper, sessionRegistry);

		when(sessionRegistry.activeRoomId("alice")).thenReturn(null);
		when(sessionRegistry.hasActiveLobbySession("alice")).thenReturn(true);

		try (ChatInbox alice = subscribe(chatService, "alice");
				 ChatInbox bob = subscribe(chatService, "bob");
				 ChatInbox carol = subscribe(chatService, "carol")) {
			chatService.moveUserToRoom("carol", "sandbox-1");

			chatService.handleChatMessage("alice", objectMapper.valueToTree(new ChatMessage("ignored", "global", "hi lobby")))
					.block(Duration.ofSeconds(1));

			ChatMessage aliceReceived = alice.await(message -> "hi lobby".equals(message.getText()));
			assertThat(aliceReceived.getFrom()).isEqualTo("alice");
			assertThat(aliceReceived.getTo()).isEqualTo("group:lobby");

			ChatMessage bobReceived = bob.await(message -> "hi lobby".equals(message.getText()));
			assertThat(bobReceived.getFrom()).isEqualTo("alice");
			assertThat(bobReceived.getTo()).isEqualTo("group:lobby");

			carol.assertNoMessage(message -> "hi lobby".equals(message.getText()));
		}
	}

	@Test
	void roomScopedMessage_isDeliveredOnlyInsideCurrentRoom_andRequestedTargetIsIgnored() {
		GameSessionRegistry sessionRegistry = mock(GameSessionRegistry.class);
		ChatService chatService = new ChatService(objectMapper, sessionRegistry);

		when(sessionRegistry.activeRoomId("alice")).thenReturn("sandbox-1");
		when(sessionRegistry.hasActiveLobbySession("alice")).thenReturn(false);

		try (ChatInbox alice = subscribe(chatService, "alice");
				 ChatInbox bob = subscribe(chatService, "bob");
				 ChatInbox carol = subscribe(chatService, "carol")) {
			chatService.moveUserToRoom("alice", "sandbox-1");
			chatService.moveUserToRoom("bob", "sandbox-2");
			chatService.moveUserToRoom("carol", "sandbox-1");

			chatService.handleChatMessage(
					"alice",
					objectMapper.valueToTree(new ChatMessage("ignored", "group:room:sandbox-2", "room scoped"))
			).block(Duration.ofSeconds(1));

			ChatMessage aliceReceived = alice.await(message -> "room scoped".equals(message.getText()));
			assertThat(aliceReceived.getTo()).isEqualTo("group:room:sandbox-1");

			ChatMessage carolReceived = carol.await(message -> "room scoped".equals(message.getText()));
			assertThat(carolReceived.getTo()).isEqualTo("group:room:sandbox-1");

			bob.assertNoMessage(message -> "room scoped".equals(message.getText()));
		}
	}

	@Test
	void directMessage_isDeliveredToRecipient_andCopiedToSender() {
		GameSessionRegistry sessionRegistry = mock(GameSessionRegistry.class);
		ChatService chatService = new ChatService(objectMapper, sessionRegistry);

		try (ChatInbox alice = subscribe(chatService, "alice");
				 ChatInbox bob = subscribe(chatService, "bob")) {
			chatService.handleChatMessage("alice", objectMapper.valueToTree(new ChatMessage("ignored", "user:bob", "pm")))
					.block(Duration.ofSeconds(1));

			ChatMessage bobReceived = bob.await(message -> "pm".equals(message.getText()));
			assertThat(bobReceived.getFrom()).isEqualTo("alice");
			assertThat(bobReceived.getTo()).isEqualTo("user:bob");

			ChatMessage aliceReceived = alice.await(message -> "pm".equals(message.getText()));
			assertThat(aliceReceived.getFrom()).isEqualTo("alice");
			assertThat(aliceReceived.getTo()).isEqualTo("user:bob");
		}
	}

	private ChatInbox subscribe(ChatService chatService, String username) {
		LinkedBlockingQueue<ChatMessage> messages = new LinkedBlockingQueue<>();
		Disposable subscription = chatMessages(chatService.initialize(username)).subscribe(messages::offer);
		return new ChatInbox(messages, subscription);
	}

	private static reactor.core.publisher.Flux<ChatMessage> chatMessages(reactor.core.publisher.Flux<MessageOutput> outputs) {
		return outputs
				.filter(o -> o.getType() == MessageType.CHAT_MESSAGE)
				.map(o -> (ChatMessage) o.getPayload());
	}

	private static final class ChatInbox implements AutoCloseable {
		private final LinkedBlockingQueue<ChatMessage> messages;
		private final Disposable subscription;

		private ChatInbox(LinkedBlockingQueue<ChatMessage> messages, Disposable subscription) {
			this.messages = messages;
			this.subscription = subscription;
		}

		private ChatMessage await(Predicate<ChatMessage> predicate) {
			long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
			while (System.nanoTime() < deadline) {
				long remainingMillis = Math.max(1L, (deadline - System.nanoTime()) / 1_000_000L);
				try {
					ChatMessage message = messages.poll(remainingMillis, TimeUnit.MILLISECONDS);
					if (message != null && predicate.test(message)) {
						return message;
					}
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new AssertionError("Interrupted while waiting for chat message", exception);
				}
			}
			throw new AssertionError("Timed out waiting for chat message");
		}

		private void assertNoMessage(Predicate<ChatMessage> predicate) {
			assertThatThrownBy(() -> {
				long deadline = System.nanoTime() + Duration.ofMillis(250).toNanos();
				while (System.nanoTime() < deadline) {
					long remainingMillis = Math.max(1L, (deadline - System.nanoTime()) / 1_000_000L);
					try {
						ChatMessage message = messages.poll(remainingMillis, TimeUnit.MILLISECONDS);
						if (message != null && predicate.test(message)) {
							throw new AssertionError("Unexpected matching chat message observed: " + message);
						}
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
						throw new AssertionError("Interrupted while checking chat inbox", exception);
					}
				}
				throw new IllegalStateException(new TimeoutException("No matching message observed"));
			}).isInstanceOf(IllegalStateException.class)
				.hasCauseInstanceOf(TimeoutException.class);
		}

		@Override
		public void close() {
			subscription.dispose();
		}
	}
}

