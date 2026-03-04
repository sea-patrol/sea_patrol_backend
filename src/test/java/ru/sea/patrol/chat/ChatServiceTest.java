package ru.sea.patrol.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import ru.sea.patrol.dto.websocket.ChatMessage;
import ru.sea.patrol.dto.websocket.MessageInput;
import ru.sea.patrol.dto.websocket.MessageOutput;
import ru.sea.patrol.service.chat.ChatService;
import ru.sea.patrol.MessageType;

class ChatServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void initialize_emitsSystemJoinMessage() {
		ChatService chatService = new ChatService();

		Flux<ChatMessage> aliceMessages = chatMessages(chatService.initialize("alice"));

		StepVerifier.create(aliceMessages.take(1))
				.assertNext(msg -> {
					assertThat(msg.getFrom()).isEqualTo("system");
					assertThat(msg.getTo()).isEqualTo("global");
					assertThat(msg.getText()).contains("alice").contains("joined");
				})
				.verifyComplete();
	}

	@Test
	void globalMessage_isDeliveredToAllUsers() {
		ChatService chatService = new ChatService();

		Flux<ChatMessage> alice = chatMessages(chatService.initialize("alice"));
		Flux<ChatMessage> bob = chatMessages(chatService.initialize("bob"));

		chatService.handleChatMessage("alice", objectMapper.valueToTree(new ChatMessage("ignored", "global", "hi")))
				.block(Duration.ofSeconds(1));

		StepVerifier.create(alice.filter(m -> "hi".equals(m.getText())).take(1))
				.assertNext(m -> assertThat(m.getFrom()).isEqualTo("alice"))
				.verifyComplete();

		StepVerifier.create(bob.filter(m -> "hi".equals(m.getText())).take(1))
				.assertNext(m -> assertThat(m.getFrom()).isEqualTo("alice"))
				.verifyComplete();
	}

	@Test
	void directMessage_isDeliveredToRecipient_andCopiedToSender() {
		ChatService chatService = new ChatService();

		Flux<ChatMessage> alice = chatMessages(chatService.initialize("alice"));
		Flux<ChatMessage> bob = chatMessages(chatService.initialize("bob"));

		chatService.handleChatMessage("alice", objectMapper.valueToTree(new ChatMessage("ignored", "user:bob", "pm")))
				.block(Duration.ofSeconds(1));

		StepVerifier.create(bob.filter(m -> "pm".equals(m.getText())).take(1))
				.assertNext(m -> {
					assertThat(m.getFrom()).isEqualTo("alice");
					assertThat(m.getTo()).isEqualTo("user:bob");
				})
				.verifyComplete();

		StepVerifier.create(alice.filter(m -> "pm".equals(m.getText())).take(1))
				.assertNext(m -> {
					assertThat(m.getFrom()).isEqualTo("alice");
					assertThat(m.getTo()).isEqualTo("user:bob");
				})
				.verifyComplete();
	}

	@Test
	void groupMessage_isDeliveredOnlyToJoinedUsers() {
		ChatService chatService = new ChatService();

		Flux<ChatMessage> alice = chatMessages(chatService.initialize("alice"));
		Flux<ChatMessage> bob = chatMessages(chatService.initialize("bob"));

		String group = "group:party-1";

		chatService.handle("alice", new MessageInput(MessageType.CHAT_JOIN, objectMapper.valueToTree(group)))
				.block(Duration.ofSeconds(1));

		chatService.handleChatMessage("alice", objectMapper.valueToTree(new ChatMessage("ignored", group, "group hello")))
				.block(Duration.ofSeconds(1));

		StepVerifier.create(alice.filter(m -> "group hello".equals(m.getText())).take(1))
				.assertNext(m -> {
					assertThat(m.getFrom()).isEqualTo("alice");
					assertThat(m.getTo()).isEqualTo(group);
				})
				.verifyComplete();

		StepVerifier.create(bob.filter(m -> "group hello".equals(m.getText())))
				.expectNoEvent(Duration.ofMillis(200))
				.thenCancel()
				.verify();
	}

	private static Flux<ChatMessage> chatMessages(Flux<MessageOutput> outputs) {
		return outputs
				.filter(o -> o.getType() == MessageType.CHAT_MESSAGE)
				.map(o -> (ChatMessage) o.getPayload());
	}
}

