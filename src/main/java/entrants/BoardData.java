package entrants;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedList;

import pacman.game.Constants.GHOST;
import pacman.game.comms.Message;
import pacman.game.comms.Message.MessageType;
import pacman.game.Game;
import pacman.game.internal.Maze;
import pacman.game.internal.Node;


/** Co najmniej te metody chcemy udostępniać. */
interface IBoardData {
	public void update(Game game);
	public SmartMessenger getSmartMessenger();
	public String toString();
	
	public boolean isWall(int x, int y);
	public boolean isWall(int index);
	public boolean isEmpty(int x, int y);
	public boolean isEmpty(int index);
	public boolean isPill(int x, int y);
	public boolean isPill(int index);
	public boolean isPowerPill(int x, int y);
	public boolean isPowerPill(int index);
	public boolean isPacman(int x, int y, int treshold);
	public boolean isPacman(int index, int treshold);
	public boolean isGhost(GHOST ghost, int x, int y, int treshold);
	public boolean isGhost(GHOST ghost, int index, int treshold);
	
	public void setPacmanIndex(int index);
	public void setPacmanIndex(int index, int time);
	public DataTime getPacmanIndex();
	public void setGhostIndex(GHOST ghost, int index);
	public void setGhostIndex(GHOST ghost, int index, int time);
	public DataTime getGhostIndex(GHOST ghost);
	public LinkedList<Integer> getRemainingPillIndices();
	public LinkedList<Integer> getRemainingPowerPillsIndices();
}


/** Instancja klasy BoardData powinna być zmienną instancji kontrolera 
 *  danego duszka / pacmana. Kontruktor bez parametrów jest odpowiedni dla 
 *  kontrolera pacmana, dla kontrolera duszka trzeba wywołać go z nazwą duszka
 *  i flagą mówiącą czy chcemy użyć wysyłania i odbierania wiadomości o zobaczeniu
 *  pacmana i pozycjach duszków. Na początku każdego przebiegu funkcji getMove
 *  w kontrolerze należy wywołać metodę update(Game game) obiektu klasy BoardData.
 *  Można sobie wyprintować obiekt BoardData (planszę i inne dane) dzięki toString(). */
public class BoardData implements IBoardData {
	private int height = 120;
	private int width = 109;
	private char[][] board = new char[height][width];
	private char wallChar = '.';
	private char pillChar = '*';
	private char powerPillChar = '$';
	private char corridorChar = ' ';
	private char pacmanChar = '@';
	
	private Game game;
	private Node[] nodes;
	private Maze maze;
	private LinkedList<Integer> pillIndices;
	private LinkedList<Integer> powerPillIndices;
	private int level = -1;
	private final int pacmanInitIndex = 978;
	
	private DataTime pacmanIndex;
	private EnumMap<GHOST, DataTime> ghostIndices = new EnumMap<>(GHOST.class);

	private final GHOST clientGhost;
	private boolean messaging = false;
	private SmartMessenger smartMessanger;
	
	
	/** Konstruktor dla pacmana. */
	public BoardData() {
		this.clientGhost = null;
	}
	/** Konstruktor dla duszka. Flaga messaging odpowiada za włączenie 
	 *  wysyłania i odbierania wiadomości o zobaczeniu pacmana i pozycji duszka. */
	public BoardData(GHOST clientGhost, boolean messaging) {
		this.messaging = messaging;
		this.clientGhost = clientGhost;
	}
	
	/** Inicjuje planszę i informacje, gdy rozpoczyna się nowy poziom. */
	private void initBoard() {
		level = game.getCurrentLevel();
		
		// na początek wszystko jest ścianą
		for (int x = 0; x < width; ++x) {
			for (int y = 0; y < height; ++y) {
				setBoardElement(x, y, wallChar);
			}
		}
		
		// budujemy korytarze
    	maze = game.getCurrentMaze();
    	nodes = maze.graph;
		for (Node node : nodes) {
			setBoardElement(node.x, node.y, corridorChar);
		}
		
		// dostawiamy tabletki
		pillIndices = new LinkedList<Integer>();
		for (int pillIndex : maze.pillIndices) {
			pillIndices.add(pillIndex);
			setBoardElement(pillIndex, pillChar);
		}
		
		// dostawiamy potężne tabletki
		powerPillIndices = new LinkedList<Integer>();
		for (int powerPillIndex : maze.powerPillIndices) {
			powerPillIndices.add(powerPillIndex);
			setBoardElement(powerPillIndex, powerPillChar);
		}
		
		// inicjacja początkowymi pozycjami pacmana i duchów
		initPositions();
		
		if (messaging) {
			smartMessanger = new SmartMessenger(clientGhost, game);
		}
	}
	
	/** Aktualizacja stanów. Metoda powinna być wywołana na początku
	 *  każdego przebiegu funkcji getMove. */
	public void update(Game game) {
		this.game = game;
		if (level < game.getCurrentLevel()) {
			initBoard();
		}
    	
		updatePills();
		updatePowerPills();
		updatePacman();
		updateGhosts();
		if (game.wasPacManEaten()) {
			initPositions();
		}
		if (messaging) {
			smartMessanger.update(game);
			getAndProcessMessages();
		}
	}
	
	private void updatePills() {
		Iterator<Integer> it = pillIndices.iterator();
		while (it.hasNext()) {
			int pillIndex = it.next();
			if (isPill(pillIndex) &&
							game.isNodeObservable(pillIndex) &&
							!game.isPillStillAvailable(game.getPillIndex(pillIndex))) {
				clearElement(pillIndex);
				it.remove();
			}
		}
	}
	
	private void updatePowerPills() {
		Iterator<Integer> it = powerPillIndices.iterator();
		while (it.hasNext()) {
			int powerPillIndex = it.next();
			if (isPowerPill(powerPillIndex) &&
						game.isNodeObservable(powerPillIndex) &&
						!game.isPowerPillStillAvailable(game.getPowerPillIndex(powerPillIndex))) {
				clearElement(powerPillIndex);
				it.remove();
			}
		}
	}
	
	private void updatePacman() {
		int pacmanCurrentIndex = game.getPacmanCurrentNodeIndex();
		if (pacmanCurrentIndex >= 0) {
			setPacmanIndex(pacmanCurrentIndex);
			if (messaging) {
				smartMessanger.broadcastMessagePacmanSeen(pacmanCurrentIndex);
//    			System.out.format("%d. %s wysyła info o pacmanie, że jest on w %d\n",
//    			game.getCurrentLevelTime(), clientGhost, pacmanCurrentIndex); 
			}
		}
	}
	
	private void updateGhosts() {
		for (GHOST ghost : GHOST.values()) {
			int ghostCurrentIndex = game.getGhostCurrentNodeIndex(ghost);
			if (ghostCurrentIndex >= 0) {
				setGhostIndex(ghost, ghostCurrentIndex);
				if (messaging && ghost == clientGhost) {
					smartMessanger.broadcastMessageIAm(ghostCurrentIndex);
//			    	System.out.format("%d. %s wysyła info o sobie, że jest w %d\n",
//	    			game.getCurrentLevelTime(), clientGhost, ghostCurrentIndex); 
				}
			}
		}
	}
	
	private void getAndProcessMessages() {
        for (Message message : smartMessanger.getCurrentMessages()) {
            if (message.getType() == MessageType.PACMAN_SEEN) {
//            	System.out.format("%d. %s odbiera od %s info o pacmanie podczas %d, posiadając dane z %d\n",
//            			game.getCurrentLevelTime(), clientGhost, message.getSender(), message.getTick(), getPacmanIndex().time); 

                if (message.getTick() > getPacmanIndex().time) {
                	setPacmanIndex(message.getData(), message.getTick());
                }
            }
            else if (message.getType() == MessageType.I_AM) {
//            	System.out.format("%d. %s odbiera od %s obecność podczas %d, posiadając dane z %d\n",
//                		game.getCurrentLevelTime(), clientGhost, message.getSender(), message.getTick(), getGhostIndex(message.getSender()).time); 
                if (message.getTick() > getGhostIndex(message.getSender()).time) {
                	setGhostIndex(message.getSender(), message.getData(), message.getTick());
                }
                			}
        }		
	}
	
	private void initPositions() {
		setPacmanIndex(pacmanInitIndex);
		for (GHOST ghost : GHOST.values()) {
			setGhostIndex(ghost, game.getGhostInitialNodeIndex());
		}
	}

	
	
	public boolean isWall(int x, int y) {
		return compareBoardElement(x, y, wallChar);
	}
	
	public boolean isWall(int index) {
		return isWall(indexX(index), indexY(index));
	}
	
	public boolean isEmpty(int x, int y) {
		return compareBoardElement(x, y, corridorChar);
	}
	
	public boolean isEmpty(int index) {
		return isEmpty(indexX(index), indexY(index));
	}
	
	public boolean isPill(int x, int y) {
		return compareBoardElement(x, y, pillChar);
	}
	
	public boolean isPill(int index) {
		return isPill(indexX(index), indexY(index));
	}
	
	public boolean isPowerPill(int x, int y) {
		return compareBoardElement(x, y, powerPillChar);
	}
	
	public boolean isPowerPill(int index) {
		return isPowerPill(indexX(index), indexY(index));
	}
	
	public boolean isPacman(int x, int y, int treshold) {
		return compareIndexCoords(getPacmanIndex().value, x, y) && !expired(getPacmanIndex().time, treshold);
	}
	
	public boolean isPacman(int index, int treshold) {
		return index == getPacmanIndex().value && !expired(getPacmanIndex().time, treshold);
	}

	public boolean isGhost(GHOST ghost, int x, int y, int treshold) {
		return compareIndexCoords(getGhostIndex(ghost).value, x, y) && !expired(getGhostIndex(ghost).time, treshold);
	}
	
	public boolean isGhost(GHOST ghost, int index, int treshold) {
		return index == getGhostIndex(ghost).value && !expired(getGhostIndex(ghost).time, treshold);
	}
	
	/** Sprawdza, czy wydarzenie się przeterminowało. */
	private boolean expired(int eventTime, int treshold) {
		return game.getCurrentLevelTime() - eventTime > treshold;
	}
	
	
	
	
	
	public void setPacmanIndex(int index) {
		setPacmanIndex(index, game.getCurrentLevelTime());
	}
	
	public void setPacmanIndex(int index, int time) {
		pacmanIndex = new DataTime(index, time);
	}
	
	public DataTime getPacmanIndex() {
		return pacmanIndex;
	}
	
	public void setGhostIndex(GHOST ghost, int index) {
		setGhostIndex(ghost, index, game.getCurrentLevelTime());
	}
	
	public void setGhostIndex(GHOST ghost, int index, int time) {
		ghostIndices.put(ghost, new DataTime(index, time));
	}
	
	public DataTime getGhostIndex(GHOST ghost) {
		return ghostIndices.getOrDefault(ghost, null);
	}

	public LinkedList<Integer> getRemainingPillIndices() {
		return pillIndices;
	}
	
	public LinkedList<Integer> getRemainingPowerPillsIndices() {
		return powerPillIndices;
	}
	
	private boolean compareIndexCoords(int index, int x, int y) {
		return indexX(index) == x && indexY(index) == y;
	}
	
	private void clearElement(int x, int y) {
		setBoardElement(x, y, corridorChar);
	}
	
	private void clearElement(int index) {
		clearElement(indexX(index), indexY(index));
	}
	
	private boolean compareBoardElement(int x, int y, char value) {
		return getBoardElement(x, y) == value;
	}
	
	private void setBoardElement(int x, int y, char value) {
		board[y][x] = value;
	}
	
	private void setBoardElement(int index, char value) {
		setBoardElement(indexX(index), indexY(index), value);
	}
	
	private char getBoardElement(int x, int y) {
		return board[y][x];
	}
	
	private char getBoardElement(int index) {
		return getBoardElement(indexX(index), indexY(index));
	}
	
	private int indexX(int index) {
		return nodes[index].x;
	}
	
	private int indexY(int index) {
		return nodes[index].y;
	}
	
	
	public SmartMessenger getSmartMessenger() {
		return smartMessanger;
	}
	
	
	/** Zamienia w stringa informacje o grze i planszę. Duszki są oznaczone
	 *  przez ich pierwsze litery, pacman i reszta planszy wg zmiennych wallChar itd. */
	public String toString() {
		// ogólne informacje
		String str = String.format("CURRENT GAME KNOWLEDGE OF %s\nLevel: %d, level time: %d, pacman score: %d, pacman lives: %d\n",
									(clientGhost == null ? "PACMAN" : clientGhost),
									level,
									game.getCurrentLevelTime(),
									game.getScore(),
									game.getPacmanNumberOfLivesRemaining());
		str += "Localization data time: ";
		for (GHOST ghost : GHOST.values()) {
			str += String.format("%s %d, ", ghost, getGhostIndex(ghost).time);
		}
		str += String.format("pacman %d\n", getPacmanIndex().time);
//		str += "###" + game.getGameState() + "^^^\n";
		
		// wstawienie duszków i pacmana na planszę
		char[][] boardCopy = new char[board.length][];
		for (int i = 0; i < board.length; ++i) {
			boardCopy[i] = board[i].clone();
		}
		setBoardElement(getPacmanIndex().value, pacmanChar);
		for (GHOST ghost : GHOST.values()) {
			setBoardElement(getGhostIndex(ghost).value, ghost.toString().charAt(0));
		}
		
		// zbudowanie planszy
		for (int i = 0; i < height; ++i) {
			str += String.valueOf(board[i]) + '\n';
		}
		
		// przywrócenie planszy bez duszków i pacmana
		board = boardCopy;
		return str;
	}
}


/** Struktura do zapisania danych (indeksu na planszy) i czasu pozyskania danych. */
class DataTime {
	int value;
	int time;
	public DataTime(int value, int time) {
		this.value = value;
		this.time = time;
	}
}