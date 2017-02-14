package entrants;


import java.util.*;
import java.util.stream.Collectors;

import pacman.game.Constants;
import pacman.game.Constants.GHOST;
import pacman.game.comms.Message;
import pacman.game.comms.Message.MessageType;
import pacman.game.Game;
import pacman.game.internal.Maze;
import pacman.game.internal.Node;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


enum PROBABILITY {
    UNIFORM,
    TRIANGULAR_WITH_MANHATTAN_DISTANCE,
    TRIANGULAR_WITH_REAL_DISTANCE
}

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
  public boolean isGhost(int index);
	public boolean isGhostOrGhostsNeighbor(int index);
	public boolean isNodeObservableBySomeGhost(int index);

	public void setPacmanIndex(int index);
	public void setPacmanIndex(int index, int time);
	public DataTime getPacmanIndex();
	public void setGhostIndex(GHOST ghost, int index);
	public void setGhostIndex(GHOST ghost, int index, int time);
	public void removePowerpill(GHOST ghost);
	public DataTime getGhostIndex(GHOST ghost);
	public Integer getLairIndex();
	public Integer getExactNumberOfPowerpills();
	public LinkedList<Integer> getRemainingPillIndices();
	public LinkedList<Integer> getRemainingPowerPillsIndices();
	public Integer getFloodingTime();
  public HashSet<Integer> getPossiblePacmanPositions();

	public Constants.MOVE nextMoveTowardsTarget(int initialPosition, int finalPosition, Constants.MOVE lastMove);
	public LinkedList<Integer> getNeighbors(int x, int y);
	public LinkedList<Integer> getNeighbors(int index);
  public LinkedList<Integer> getFreeNeighbors(int index);
	public LinkedList<Integer> getFreeNeighbors(int x, int y);
	public Integer getPowerpillWithShortestCycle(int position, Constants.MOVE lastMove);
	public Integer getDistanceToPowerpillWithShortestCycleNearestToSomeGhost();
	public Integer getDistanceToPowerpillWithShortestCycleNearestToGivenPosition(int position);

	public HashSet<Integer> basicFlooding(HashSet<Integer> pacmanInitialPositions, int steps);
	public HashSet<Integer> basicFlooding(HashSet<Integer> pacmanInitialPositions);
	public HashSet<Integer> basicFlooding(Integer pacmanInitialPosition);
	public HashSet<Integer> basicFlooding(Integer pacmanInitialPosition, int steps);
	public HashSet<Integer> floodingStoppedByGhost(HashSet<Integer> pacmanInitialPositions, int steps);
	public HashSet<Integer> floodingStoppedByGhost(HashSet<Integer> pacmanInitialPositions);
	public HashSet<Integer> floodingStoppedByGhost(Integer pacmanInitialPosition);
	public HashSet<Integer> floodingStoppedByGhost(Integer pacmanInitialPosition, int steps);
	public HashSet<Integer> floodingWithDeletionOnSight(HashSet<Integer> pacmanInitialPositions, int steps);
	public HashSet<Integer> floodingWithDeletionOnSight(HashSet<Integer> pacmanInitialPositions);
	public HashSet<Integer> floodingWithDeletionOnSight(Integer pacmanInitialPosition);
	public HashSet<Integer> floodingWithDeletionOnSight(Integer pacmanInitialPosition, int steps);

	public double unnormalizedProbabilityOfPacmanAtPosition(HashSet<Integer> positions, int initialPosition,
                                                            int position, int floodingTime);
	public double normalizedProbabilityOfSelectedPositions(HashSet<Integer> positions, HashSet<Integer> selectedPositions,
                                                           int initialPosition, int floodingTime);
	public double normalizedProbabilityOfPositionsVisibleFromIndex(HashSet<Integer> positions, int index,
                                                           int initialPosition, int floodingTime);
	public HashSet<Integer> positionsVisibleFromIndex(int index);

	/** Funkcja oceny w stanie CatchingState */
	public int numberOfFloodedPositions(HashSet<Integer> floodedPositios, int initialNumberOfFloodedPositions,
                                        int depthInTree);
	public int numberOfFloodedPositions(HashSet<Integer> floodedPositios);
	public int numberOfFloodedPositions();
}


/** Instancja klasy BoardData powinna być zmienną instancji kontrolera
 *  danego duszka / pacmana. Kontruktor bez parametrów jest odpowiedni dla
 *  kontrolera pacmana, dla kontrolera duszka trzeba wywołać go z nazwą duszka
 *  i flagą mówiącą czy chcemy użyć wysyłania i odbierania wiadomości o zobaczeniu
 *  pacmana i pozycjach duszków. Na początku każdego przebiegu funkcji getMove
 *  w kontrolerze należy wywołać metodę update(Game game) obiektu klasy BoardData.
 *  Można sobie wyprintować obiekt BoardData (planszę i inne dane) dzięki toString(). */
public class BoardData implements IBoardData {
    public static final int INITIAL_POSITION = -1;
    private static final PROBABILITY PROBABILITY_IN_USE = PROBABILITY.TRIANGULAR_WITH_MANHATTAN_DISTANCE;
    private static final double MIN_PROBABILITY = 0.6;


    private int height = 120;
	private int width = 109;
	private char[][] board = new char[height][width];
	private Node[][] nodeBoard = new Node[height][width];
	private char wallChar = '.';
	private char pillChar = '*';
	private char powerPillChar = '$';
	private char corridorChar = ' ';
	private char pacmanChar = '@';
	private char floodedChar = '~';


	private Game game;
	private Node[] nodes;
	private Maze maze;
	private LinkedList<Integer> pillIndices;
	private LinkedList<Integer> powerPillIndices;
	private int level = -1;
	private int pacmanInitIndex;
	private int lairIndex;

	private DataTime pacmanIndex;
	private HashSet<Integer> possiblePacmanPositions;
	private Integer floodingTime;
	private EnumMap<GHOST, DataTime> ghostIndices = new EnumMap<>(GHOST.class);
	private EnumMap<GHOST, Constants.MOVE> ghostDirections = new EnumMap<GHOST, Constants.MOVE>(GHOST.class);

	private final GHOST clientGhost;
	private boolean messaging = false;
	private SmartMessenger smartMessanger;
	private List<Integer> clientIndicesHistory;
	private int clientPosition;

	private int exactNumberOfPowerpills;


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
        exactNumberOfPowerpills = 4;
		powerPillIndices = new LinkedList<Integer>();
		for (int powerPillIndex : maze.powerPillIndices) {
			powerPillIndices.add(powerPillIndex);
			setBoardElement(powerPillIndex, powerPillChar);
		}

		// inicjacja początkowymi pozycjami pacmana i duchów
		initPositions();


		// reset historii
		clientIndicesHistory = new ArrayList<Integer>();
		
		if (messaging) {
			smartMessanger = new SmartMessenger(clientGhost, game);
		}
	}

	private void initNodeBoard() {
	    for (int x = 0; x < width; ++x) {
			for (int y = 0; y < height; ++y) {
				setNodeBoardElement(x, y, null);
			}
		}

	    maze = game.getCurrentMaze();
    	nodes = maze.graph;
		for (Node node : nodes) {
		    setNodeBoardElement(node.x, node.y, node);
        }
    }

    private void indexTest() {
        for (Node node : nodes) {
		    if (node.nodeIndex != index(indexX(node.nodeIndex), indexY(node.nodeIndex)) ||
                    node.x != indexX(index(node.x, node.y)) ||
                    node.y != indexY(index(node.x, node.y))) {
		        throw new AssertionError();
            }
        }
    }

	/** Aktualizacja stanów. Metoda powinna być wywołana na początku
	 *  każdego przebiegu funkcji getMove. */
	public void update(Game game) {
		this.game = game;
		
		if (level < game.getCurrentLevel()) {
			initBoard();
			initNodeBoard();
			indexTest();
		}


		updateClientPosition();

		updatePills();
		updatePowerPills();
		updateGhosts();
		updatePacman();
		if (game.wasPacManEaten()) {
			initPositions();
		}
		if (messaging) {
			smartMessanger.update(game);
			getAndProcessMessages();
		}
		
		clientIndicesHistory.add(clientPosition);
	}
	
	private void updateClientPosition() {
		if (clientGhost == null) {
			clientPosition = game.getPacmanCurrentNodeIndex();
		}
		else {
			clientPosition = game.getGhostCurrentNodeIndex(clientGhost);
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
	    if (game.wasPowerPillEaten()) {
	        exactNumberOfPowerpills--;
        }
		Iterator<Integer> it = powerPillIndices.iterator();
		while (it.hasNext()) {
			int powerPillIndex = it.next();
			if ((exactNumberOfPowerpills == 0) ||
                    (isPowerPill(powerPillIndex) &&
                            game.isNodeObservable(powerPillIndex) &&
                            !game.isPowerPillStillAvailable(game.getPowerPillIndex(powerPillIndex)))) {
				clearElement(powerPillIndex);
				it.remove();
				if (smartMessanger != null) {
                    smartMessanger.broadcastMessageIAmHeading(INITIAL_POSITION);
                }
			}
		}
	}

	private void updatePacman() {
		int pacmanCurrentIndex = game.getPacmanCurrentNodeIndex();
		if (pacmanCurrentIndex >= 0) {
		    possiblePacmanPositions = new HashSet<>();
		    possiblePacmanPositions.add(pacmanCurrentIndex);
		    floodingTime = 1;
			setPacmanIndex(pacmanCurrentIndex);
			if (messaging) {
				smartMessanger.broadcastMessagePacmanSeen(pacmanCurrentIndex);
//    			System.out.format("%d. %s wysyła info o pacmanie, że jest on w %d\n",
//    			game.getCurrentLevelTime(), clientGhost, pacmanCurrentIndex);
			}
		} else if (game.getCurrentLevelTime() > 0) {
		    possiblePacmanPositions = floodingWithDeletionOnSight(possiblePacmanPositions);
		    floodingTime++;
        }
	}

	private void updateGhosts() {
		for (GHOST ghost : GHOST.values()) {
			int ghostCurrentIndex = game.getGhostCurrentNodeIndex(ghost);
			if (ghostCurrentIndex >= 0) {
			    updateGhostDirection(ghost, ghostCurrentIndex);
				setGhostIndex(ghost, ghostCurrentIndex);
				if (messaging && ghost == clientGhost) {
					smartMessanger.broadcastMessageIAm(ghostCurrentIndex);
//			    	System.out.format("%d. %s wysyła info o sobie, że jest w %d\n",
//	    			game.getCurrentLevelTime(), clientGhost, ghostCurrentIndex);
				}
			}
		}
	}

    private void updateGhostDirection(GHOST ghost, int ghostCurrentIndex) {
	    int previousIndex = getGhostIndex(ghost).value;
        if (ghostDirections.get(ghost) == null ||
                game.getManhattanDistance(previousIndex, ghostCurrentIndex) > 1) {
	        setGhostDirection(ghost, Constants.MOVE.NEUTRAL);
        }
        if (game.getManhattanDistance(previousIndex, ghostCurrentIndex) == 1) {
            if (indexX(ghostCurrentIndex) == indexX(previousIndex) + 1) {
                setGhostDirection(ghost, Constants.MOVE.RIGHT);
            }
            if (indexX(ghostCurrentIndex) == indexX(previousIndex) - 1) {
                setGhostDirection(ghost, Constants.MOVE.LEFT);
            }
            if (indexY(ghostCurrentIndex) == indexY(previousIndex) + 1) {
                setGhostDirection(ghost, Constants.MOVE.DOWN);
            }
            if (indexY(ghostCurrentIndex) == indexY(previousIndex) - 1) {
                setGhostDirection(ghost, Constants.MOVE.UP);
            }
        }
    }

    private void getAndProcessMessages() {
        for (Message message : smartMessanger.getCurrentMessages()) {
            if (message.getType() == MessageType.I_AM) {
//            	System.out.format("%d. %s odbiera od %s obecność podczas %d, posiadając dane z %d\n",
//                		game.getCurrentLevelTime(), clientGhost, message.getSender(), message.getTick(), getGhostIndex(message.getSender()).time);
                if (message.getTick() > getGhostIndex(message.getSender()).time) {
                    updateGhostDirection(message.getSender(), message.getData());
                    setGhostIndex(message.getSender(), message.getData(), message.getTick());
                }
            }
            else if (message.getType() == MessageType.PACMAN_SEEN) {
//            	System.out.format("%d. %s odbiera od %s info o pacmanie podczas %d, posiadając dane z %d\n",
//            			game.getCurrentLevelTime(), clientGhost, message.getSender(), message.getTick(), getPacmanIndex().time);

                if (message.getTick() > getPacmanIndex().time) {
                	setPacmanIndex(message.getData(), message.getTick());
		            possiblePacmanPositions = floodingWithDeletionOnSight(message.getData(),
                            game.getCurrentLevelTime() - message.getTick());
		            floodingTime += game.getCurrentLevelTime() - message.getTick();
                }
            }
          
        }
	}

	private void initPositions() {
		for (GHOST ghost : GHOST.values()) {
			setGhostIndex(ghost, game.getGhostInitialNodeIndex());
		}
        lairIndex = game.getCurrentMaze().lairNodeIndex;
		pacmanInitIndex = game.getCurrentMaze().initialPacManNodeIndex;
		setPacmanIndex(pacmanInitIndex);
		possiblePacmanPositions = new HashSet<>();
		floodingTime = 1;
		possiblePacmanPositions.add(pacmanInitIndex);
		for (GHOST ghost : GHOST.values()) {
		    setGhostIndex(ghost, lairIndex);
        }
	}

    private boolean isNodeObservable(int nodeIndex, int observerIndex) {
        if (nodeIndex == -1 || observerIndex == -1 ||
                nodeIndex == lairIndex || observerIndex == lairIndex) {
            return false;
        }
        Node currentNode = nodes[observerIndex];
        Node check = nodes[nodeIndex];

        if (currentNode.x == check.x || currentNode.y == check.y) {
            double manhattan = game.getManhattanDistance(currentNode.nodeIndex, check.nodeIndex);
            double shortestPath = game.getShortestPathDistance(currentNode.nodeIndex, check.nodeIndex);
            return (manhattan == shortestPath);
        }
        return false;
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

    @Override
    public boolean isGhost(int index) {
        boolean isGhost = false;