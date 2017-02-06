package entrants;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import pacman.game.Constants.GHOST;
import pacman.game.comms.Message;
import pacman.game.comms.Message.MessageType;

interface IMessageList {
	public IMessageList selectSender(GHOST sender);
	public IMessageList excludeSender(GHOST sender);
	
	public IMessageList selectRecipient(GHOST recipient);
	public IMessageList excludeRecipient(GHOST recipient);
	
	public IMessageList selectType(MessageType type);
	public IMessageList excludeType(MessageType type);
	
	public IMessageList selectTickRange(int from, int to);
	public IMessageList selectTickFrom(int from);
	public IMessageList selectTickTo(int to);
	public IMessageList selectLastTicks(int currentTick, int ticksSpan);
}


/** Klasa pozwala na odfiltrowywanie listy wiadomości. Fitry należy wywoływać
 *  jako kolejne metody obiektu MessageList, na przykład wiadomości
 *  zapisane w zmiennej messages, które są typu PACMAN_SEEN i zostały
 *  wysłane przez SUE pomiędzy 17. a 42. tikiem otrzymamy przez 
 *  messages.selectType(PACMAN_SEEN).selectSender(SUE).selectTickRange(17, 42). */
public class MessageList extends ArrayList<Message> implements IMessageList {
	public MessageList() {
		super();
	}
	
	public MessageList(ArrayList<Message> a) {
		super(a);
	}

	
	private MessageList filterWithPredicate(Predicate<Message> predicate) {
		return this.stream().filter(predicate).collect(Collectors.toCollection(MessageList::new));
	}
	
	
	/** Wybiera te wiadomości, które zostały wysłane przez duszka sender.*/
	public MessageList selectSender(GHOST sender) {
		return filterWithPredicate(m -> m.getSender().equals(sender));
	}

	/** Wybiera te wiadomości, które NIE zostały wysłane przez duszka sender.*/
	public MessageList excludeSender(GHOST sender) {
		return filterWithPredicate(m -> !m.getSender().equals(sender));
	}

	/** Wybiera te wiadomości, które zostały otrzymane przez duszka recipient.*/
	public MessageList selectRecipient(GHOST recipient) {
		return filterWithPredicate(m -> m.getRecipient() == null || m.getRecipient().equals(recipient));
	}

	/** Wybiera te wiadomości, które NIE zostały otrzymane przez duszka recipient.*/
	public MessageList excludeRecipient(GHOST recipient) {
		return filterWithPredicate(m -> m.getRecipient() != null && !m.getRecipient().equals(recipient));
	}
	
	/** Wybiera te wiadomości, które są typu type.*/
	public MessageList selectType(MessageType type) {
		return filterWithPredicate(m -> m.getType().equals(type));
	}
	
	/** Wybiera te wiadomości, które NIE są typu type.*/
	public MessageList excludeType(MessageType type) {
		return filterWithPredicate(m -> !m.getType().equals(type));
	}
	
	/** Wybiera te wiadomości wysłane w podanym przedziale czasowym. */
	public MessageList selectTickRange(int from, int to) {
		return filterWithPredicate(m -> from <= m.getTick() && m.getTick() <= to);
	}

	/** Wybiera te wiadomości wysłane w tiku from lub później. */
	public MessageList selectTickFrom(int from) {
		return filterWithPredicate(m -> from <= m.getTick());
	}
	
	/** Wybiera te wiadomości wysłane w tiku to lub wcześniej. */
	public MessageList selectTickTo(int to) {
		return filterWithPredicate(m -> m.getTick() <= to);
	}
	
	/** Wybiera te wiadomości, które zostały wysłane w okresie ticksSpan
	 *  tików przed tickiem currentTick. */
	public MessageList selectLastTicks(int currentTick, int ticksSpan) {
		return selectTickFrom(currentTick - ticksSpan);
	}
	
	
	private static final long serialVersionUID = 1L;	// bez tego wyskakiwało ostrzeżenie
}