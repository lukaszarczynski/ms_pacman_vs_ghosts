package entrants;

import pacman.game.Game;
import pacman.game.Constants.GHOST;
import pacman.game.comms.Messenger;
import pacman.game.comms.BasicMessage;
import pacman.game.comms.Message;
import pacman.game.comms.Message.MessageType;

interface ISmartMessanger {
	public void update(Game game);
	
	public void sendRawMessage(GHOST recipient, MessageType type, int data, int tick);
	public void broadcastRawMessage(MessageType type, int data, int tick);
	public void sendMessage(GHOST recipient, MessageType type, int data);
	public void broadcastMessage(MessageType type, int data);	
	public void broadcastMessagePacmanSeen(int data);
	public void broadcastMessagePacmanSeen();
	public void broadcastMessageIAm(int data);
	public void broadcastMessageIAm();
	public void broadcastMessageIAmHeading(int data);
	
	public MessageList getCurrentMessages();
	public MessageList getMessagesHistory();
}


/** Klasa do wysyłania wiadomości. Przechowuje także historię odebranych
 *  i wysłanych przez siebie wiadomości. */
public class SmartMessenger implements ISmartMessanger {
	private Game game;
	private Messenger defaultMessenger;
	private GHOST senderGhost;
	private MessageList messagesHistory;
	
	/** Konstruktory klasy SmartMessenger. Być może wygodnie nie wywoływać ich
	 *  nigdzie bezpośrednio, tylko wziąć potrzebny obiekt z obiektu klasy
	 *  BoardData poprzez metodę getSmartMessenger(). */
	public SmartMessenger(GHOST sender) {
		this.senderGhost = sender;
		messagesHistory = new MessageList();
	}
	public SmartMessenger(GHOST sender, Game game) {
		this(sender);
		update(game);
	}
	
	
	/** Wysyła wiadomość. Metoda pozwala ustawić najwięcej:
	 *  @param recipient odbiorca wiadomości (jeśli jest nim null, to wysyła do wszystkich), 
	 *  @param type typ wiadomości, 
	 * 	@param data int z danymi, którym zasadniczo ma być index pola, którego dotyczy wiadomość,
	 *  @param tick int, który zasadniczo oznacza czas wysłania wiadomości.
	 *  
	 *  Technicznie nic nie stoi na przeszkodzie, aby w tych intach wysyłać cokolwiek. */
	public void sendRawMessage(GHOST recipient, MessageType type, int data, int tick) {
		BasicMessage newMessage = new BasicMessage(senderGhost, recipient, type, data, tick);
		defaultMessenger.addMessage(newMessage);
		messagesHistory.add(newMessage);
	}
	
	/** Wersja metody sendRawMessage, która wymusza wysłanie wiadomości do wszystkich. */
	public void broadcastRawMessage(MessageType type, int data, int tick) {
		sendRawMessage(null, type, data, tick);
	}
	
	/** Wersja metody sendRawMessage, która wymusza wysłanie wiadomości z bieżącym czasem. */
	public void sendMessage(GHOST recipient, MessageType type, int data) {
		sendRawMessage(recipient, type, data, game.getCurrentLevelTime());
	}
	
	/** Wersja metody sendMessage, która wymusza wysłanie wiadomości do wszystkich. */
	public void broadcastMessage(MessageType type, int data) {
		sendMessage(null, type, data);
	}
	
	
	// Metody z ustawionymi konkretnymi typami wiadomości.
	
	public void broadcastMessagePacmanSeen(int data) {
		broadcastMessage(MessageType.PACMAN_SEEN, data);
	}
	
	public void broadcastMessagePacmanSeen() {
		broadcastMessagePacmanSeen(game.getPacmanCurrentNodeIndex());
	}

	public void broadcastMessageIAm(int data) {
		broadcastMessage(MessageType.I_AM, data);
	}
	
	public void broadcastMessageIAm() {
		broadcastMessageIAm(game.getGhostCurrentNodeIndex(senderGhost));
	}

	public void broadcastMessageIAmHeading(int data) {
		broadcastMessage(MessageType.I_AM_HEADING, data);
	}
	
	
	
	/** Zwraca tablicę z wiadomościami przeznaczonymi dla danego duszka, 
	 *  które przyszły w bieżącym tiku. */
	public MessageList getCurrentMessages() {
		return new MessageList(defaultMessenger.getMessages(senderGhost));
	}
	
	/** Zwraca tablicę z wiadomościami wysłanymi i odebranymi przez danego duszka
	 *  w czasie życia obiektu w bieżącym poziomie. */
	public MessageList getMessagesHistory() {
		return messagesHistory;
	}
	
	
	
	
	/** Jeśli konstruujemy obiekt SmartMessage poza metodą getMove, to metoda
	 *  update powinna być wywołana na początku każdego przebiegu funkcji getMove.
	 *  Metoda update jest już wywoływana w klasie BoardData dla posiadanego
	 *  przez nią obiektu SmartMessage. */
	public void update(Game game) {
		this.game = game;
		defaultMessenger = game.getMessenger();
		for (Message message : getCurrentMessages()) {
			messagesHistory.add(message);
		}
	}
	
}
