package ru.sea.patrol.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import ru.sea.patrol.service.chat.ChatService;
import ru.sea.patrol.ws.protocol.MessageType;
import ru.sea.patrol.ws.protocol.dto.ChatMessage;
import ru.sea.patrol.ws.protocol.dto.MessageInput;
import ru.sea.patrol.ws.protocol.dto.MessageOutput;

class ChatServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void initialize_emitsSystemJoinMessage() {
		ChatService chatService = new ChatService();

		Flux<ChatMessage> aliceMessages = chatMessages(chatService.initialize("alice"));

		ChatMessage msg = aliceMessages.next().block(Duration.ofSeconds(1));
		assertThat(msg).isNotNull();
		assertThat(msg.getFrom()).isEqualTo("system");
		assertThat(msg.getTo()).isEqualTo("global");
		assertThat(msg.getText()).contains("alice").contains("joined");
	}

	@Test
	void globalMessage_isDeliveredToAllUsers() {
		ChatService chatService = new ChatService();

		Flux<ChatMessage> alice = chatMessages(chatService.initialize("alice"));
		Flux<ChatMessage> bob = chatMessages(chatService.initialize("bob"));

		chatService.handleChatMessage("alice", objectMapper.valueToTree(new ChatMessage("ignored", "global", "hi")))
				.block(Duration.ofSeconds(1));

		ChatMessage aliceReceived = alice.filter(m -> "hi".equals(m.getText())).next().block(Duration.ofSeconds(1));
		assertThat(aliceReceived).isNotNull();
		assertThat(aliceReceived.getFrom()).isEqualTo("alice");

		ChatMessage bobReceived = bob.filter(m -> "hi".equals(m.getText())).next().block(Duration.ofSeconds(1));
		assertThat(bobReceived).isNotNull();
		assertThat(bobReceived.getFrom()).isEqualTo("alice");
	}

	@Test
	void directMessage_isDeliveredToRecipient_andCopiedToSender() {
		ChatService chatService = new ChatService();

		Flux<ChatMessage> alice = chatMessages(chatService.initialize("alice"));
		Flux<ChatMessage> bob = chatMessages(chatService.initialize("bob"));

		chatService.handleChatMessage("alice", objectMapper.valueToTree(new ChatMessage("ignored", "user:bob", "pm")))
				.block(Duration.ofSeconds(1));

		ChatMessage bobReceived = bob.filter(m -> "pm".equals(m.getText())).next().block(Duration.ofSeconds(1));
		assertThat(bobReceived).isNotNull();
		assertThat(bobReceived.getFrom()).isEqualTo("alice");
		assertThat(bobReceived.getTo()).isEqualTo("user:bob");

		ChatMessage aliceReceived = alice.filter(m -> "pm".equals(m.getText())).next().block(Duration.ofSeconds(1));
		assertThat(aliceReceived).isNotNull();
		assertThat(aliceReceived.getFrom()).isEqualTo("alice");
		assertThat(aliceReceived.getTo()).isEqualTo("user:bob");
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

		ChatMessage aliceReceived = alice.filter(m -> "group hello".equals(m.getText())).next().block(Duration.ofSeconds(1));
		assertThat(aliceReceived).isNotNull();
		assertThat(aliceReceived.getFrom()).isEqualTo("alice");
		assertThat(aliceReceived.getTo()).isEqualTo(group);

		assertThatThrownBy(() -> bob.filter(m -> "group hello".equals(m.getText())).next().block(Duration.ofMillis(200)))
				.isInstanceOf(IllegalStateException.class)
				.hasCauseInstanceOf(TimeoutException.class);
	}

	private static Flux<ChatMessage> chatMessages(Flux<MessageOutput> outputs) {
		return outputs
				.filter(o -> o.getType() == MessageType.CHAT_MESSAGE)
				.map(o -> (ChatMessage) o.getPayload());
	}
}
