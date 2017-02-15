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

import static java.lang.Math.sqrt;


enum PROBABILITY {
    UNIFORM,
    TRIANGULAR_WITH_MANHATTAN_DISTANCE,
    TRIANGULAR_WITH_REAL_DISTANCE
}

/** Co najmniej te metody chcemy udostępniać. */
interface IBoardData {
	 void update(Game game);
	 SmartMessenger getSmartMessenger();
	 String toString();

	 boolean isWall(int x, int y);
	 boolean isWall(int index);
	 boolean isEmpty(int x, int y);
	 boolean isEmpty(int index);
	 boolean isPill(int x, int y);
	 boolean isPill(int index);
	 boolean isPowerPill(int x, int y);
	 boolean isPowerPill(int index);
	 boolean isPacman(int x, int y, int treshold);
	 boolean isPacman(int index, int treshold);
	 boolean isGhost(GHOST ghost, int x, int y, int treshold);
	 boolean isGhost(GHOST ghost, int index, int treshold);
	 boolean isGhost(int index);
	 boolean isGhostOrGhostsNeighbor(int index);
	 boolean isNodeObservableBySomeGhost(int index);

	 void setPacmanIndex(int index);
	 void setPacmanIndex(int index, int time);
	 DataTime getPacmanIndex();
	 int getPacmanIndexValue();
	 void setGhostIndex(GHOST ghost, int index);
	 void setGhostIndex(GHOST ghost, int index, int time);
	 void removePowerpill(GHOST ghost);
	 DataTime getGhostIndex(GHOST ghost);
	 Integer getLairIndex();
	 Integer getExactNumberOfPowerpills();
	 LinkedList<Integer> getRemainingPillIndices();
	 LinkedList<Integer> getRemainingPowerPillsIndices();
	 Integer getFloodingTime();
     HashSet<Integer> getPossiblePacmanPositions();

	 Constants.MOVE nextMoveTowardsTarget(int initialPosition, int finalPosition, Constants.MOVE lastMove);
	 LinkedList<Integer> getNeighbors(int x, int y);
	 LinkedList<Integer> getNeighbors(int index);
	 LinkedList<Integer> getFreeNeighbors(int index);
	 LinkedList<Integer> getFreeNeighbors(int x, int y);
	 Integer getPowerpillWithShortestCycle(int position, Constants.MOVE lastMove);
	 Integer getDistanceToPowerpillWithShortestCycleNearestToSomeGhost();
	 Integer getDistanceToPowerpillWithShortestCycleNearestToGivenPosition(int position);

	 HashSet<Integer> basicFlooding(HashSet<Integer> pacmanInitialPositions, int steps);
	 HashSet<Integer> basicFlooding(HashSet<Integer> pacmanInitialPositions);
	 HashSet<Integer> basicFlooding(Integer pacmanInitialPosition);
	 HashSet<Integer> basicFlooding(Integer pacmanInitialPosition, int steps);
	 HashSet<Integer> floodingStoppedByGhost(HashSet<Integer> pacmanInitialPositions, int steps, HashMap<GHOST, Integer> ghostPositions);
	 HashSet<Integer> floodingStoppedByGhost(HashSet<Integer> pacmanInitialPositions, int steps);
	 HashSet<Integer> floodingStoppedByGhost(HashSet<Integer> pacmanInitialPositions);
	 HashSet<Integer> floodingStoppedByGhost(Integer pacmanInitialPosition);
	 HashSet<Integer> floodingStoppedByGhost(Integer pacmanInitialPosition, int steps);
	
	 HashSet<Integer> floodingWithDeletionOnSight(HashSet<Integer> pacmanInitialPositions, int steps, HashMap<GHOST, Integer> ghostPositions);
	 HashSet<Integer> floodingWithDeletionOnSight(HashSet<Integer> pacmanInitialPositions, int steps);
	 HashSet<Integer> floodingWithDeletionOnSight(HashSet<Integer> pacmanInitialPositions);
	 HashSet<Integer> floodingWithDeletionOnSight(Integer pacmanInitialPosition);
	 HashSet<Integer> floodingWithDeletionOnSight(Integer pacmanInitialPosition, int steps);

	 double unnormalizedProbabilityOfPacmanAtPosition(HashSet<Integer> positions, int initialPosition,
                                                            int position, int floodingTime);
	 double normalizedProbabilityOfSelectedPositions(HashSet<Integer> positions, HashSet<Integer> selectedPositions,
                                                           int initialPosition, int floodingTime, int myPosition);
	 double normalizedProbabilityOfPositionsVisibleFromIndex(HashSet<Integer> positions, int index,
                                                           int initialPosition, int floodingTime);
	 double retreatStateEvaluationFunction(HashSet<Integer> positions, int index,
                                                           int initialPosition, int floodingTime);
	 double searchingStateEvaluationFunction(HashSet<Integer> positions, int index,
                                                                   int initialPosition, int floodingTime);
	 HashSet<Integer> positionsVisibleFromIndex(int index);

	 int numberOfFloodedPositions(HashSet<Integer> floodedPositios);
	 int numberOfFloodedPositions();
	 double catchingStateEvaluationFunction(HashSet<Integer> floodedPositios, double initialNumberOfFloodedPositions,
                                        int depthInTree, int lastPacmanPosition, int myPosition);

     HashMap<GHOST, Integer> getGhostIndices();
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
        for (GHOST ghost : GHOST.values()) {
            if (index == getGhostIndex(ghost).value) {
                isGhost = true;
            }
        }
        return isGhost;
    }

    @Override
    public boolean isGhostOrGhostsNeighbor(int index) {
        if (isGhost(index)) {
            return true;
        }
        LinkedList<Integer> neighborhood = getNeighbors(index);
        for (int neighbor : neighborhood) {
            if (isGhost(neighbor)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isGhostOrGhostsNeighbor(int index, HashMap<GHOST, Integer> ghostPositions) {
        if (ghostPositions.values().contains(index)) {
            return true;
        }
        LinkedList<Integer> neighborhood = getNeighbors(index);
        for (int neighbor : neighborhood) {
            if (ghostPositions.values().contains(neighbor)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isNodeObservableBySomeGhost(int index) {
        boolean nodeObservableBySomeGhost = false;
        for (GHOST ghost : GHOST.values()) {
            if (isNodeObservable(index, getGhostIndex(ghost).value)) {
                nodeObservableBySomeGhost = true;
            }
        }
        return nodeObservableBySomeGhost;
    }

    /** Sprawdza, czy wydarzenie się przeterminowało. */
	public boolean expired(int eventTime, int treshold) {
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

	@Override
    public int getPacmanIndexValue() {
		return pacmanIndex.value;
	}

	public void setGhostIndex(GHOST ghost, int index) {
		setGhostIndex(ghost, index, game.getCurrentLevelTime());
	}

	public void setGhostIndex(GHOST ghost, int index, int time) {
		ghostIndices.put(ghost, new DataTime(index, time));
	}

	private void setGhostDirection(GHOST ghost, Constants.MOVE direction) {
	    ghostDirections.put(ghost, direction);
    }

    @Override
    public void removePowerpill(GHOST observingGhost) {
		Iterator<Integer> it = powerPillIndices.iterator();
		while (it.hasNext()) {
			int powerPillIndex = it.next();
			if (isPowerPill(powerPillIndex) &&
                    isNodeObservable(powerPillIndex, ghostIndices.get(observingGhost).value)) {
				clearElement(powerPillIndex);
				it.remove();
			}
		}
    }

    public DataTime getGhostIndex(GHOST ghost) {
		return ghostIndices.getOrDefault(ghost, null);
	}

	public HashMap<GHOST, Integer> getGhostsPositions() {
	    return getGhostIndices();
    }


    @Override
    public HashMap<GHOST, Integer> getGhostIndices() {
        HashMap<GHOST, Integer> ghostIndices = new HashMap<>();
        for (GHOST ghost : GHOST.values()) {
            ghostIndices.put(ghost, getGhostIndex(ghost).value);
        }
        return ghostIndices;
    }

    public EnumMap<GHOST, Constants.MOVE> getGhostsDirections() {
	    return ghostDirections;
    }

    @Override
    public Integer getLairIndex() {
        return lairIndex;
    }

    @Override
    public Integer getExactNumberOfPowerpills() {
        return exactNumberOfPowerpills;
    }

    public LinkedList<Integer> getRemainingPillIndices() {
		return pillIndices;
	}

	public LinkedList<Integer> getRemainingPowerPillsIndices() {
		return powerPillIndices;
	}

    @Override
    public Constants.MOVE nextMoveTowardsTarget(int initialPosition, int finalPosition, Constants.MOVE lastMove) {
        int[] path;
        if (lastMove != Constants.MOVE.NEUTRAL){
            path = game.getShortestPath(initialPosition, finalPosition, lastMove);
        } else {
            path = game.getShortestPath(initialPosition, finalPosition);
        }

        Constants.MOVE move = game.getNextMoveTowardsTarget(
                initialPosition,
                path[0],
                lastMove,
                Constants.DM.MANHATTAN
        );
        return move;
    }

    @Override
    public LinkedList<Integer> getNeighbors(int x, int y) {

        Node[] nodeNeighborhood = {
                nodeBoard[y][(x - 1 + width) % width],
                nodeBoard[y][(x + 1) % width],
                nodeBoard[(y - 1 + height) % height][x],
                nodeBoard[(y + 1) % height][x]
        };
        LinkedList<Integer> neighborsIndices = new LinkedList<>();
        for (Node node : nodeNeighborhood) {
            if (node != null) {
                neighborsIndices.add(node.nodeIndex);
            }
        }
        return neighborsIndices;
    }

    @Override
    public LinkedList<Integer> getNeighbors(int index) {
	    return getNeighbors(indexX(index), indexY(index));
    }

	@Override
	public LinkedList<Integer> getFreeNeighbors(int index) {
		return getFreeNeighbors(indexX(index), indexY(index));
	}

	/** Zwraca sąsiadów, którzy nie są ścianą */
	@Override
	public LinkedList<Integer> getFreeNeighbors(int x, int y) {
		return getNeighbors(x, y).stream().filter(neighbor -> !isWall(neighbor)).
				collect(Collectors.toCollection(LinkedList::new));
	}

	@Override
    public Integer getPowerpillWithShortestCycle(int position, Constants.MOVE lastMove) {
        LinkedList<Integer> powerpillIndices = getRemainingPowerPillsIndices();
	    if (powerpillIndices.size() == 0) {
	        throw new ArrayStoreException();
        }
        int selectedPowerpill = powerpillIndices.getFirst();
	    int currentCycleLength = cycleLength(selectedPowerpill);
	    int currentDistance;
		currentDistance = Integer.MAX_VALUE;

        for (int powerPillIndex : powerpillIndices) {
            int newCycleLength = cycleLength(powerPillIndex);
            int newDistance;
			newDistance = game.getShortestPath(position, powerPillIndex, lastMove).length;

            if (newCycleLength < currentCycleLength ||
                    ((newCycleLength == currentCycleLength) && newDistance < currentDistance)) {
                selectedPowerpill = powerPillIndex;
                currentCycleLength = newCycleLength;
                currentDistance = newDistance;
            }
        }
        return selectedPowerpill;
    }

    @Override
    public Integer getDistanceToPowerpillWithShortestCycleNearestToSomeGhost() {
	    LinkedList<Integer> powerpillIndices = getRemainingPowerPillsIndices();
        if (powerpillIndices.size() == 0) {
	        throw new ArrayStoreException();
        }
        int selectedPowerpill = powerpillIndices.getFirst();
	    int currentCycleLength = cycleLength(selectedPowerpill);
	    int currentDistance;
		currentDistance = Integer.MAX_VALUE;

        for (int powerPillIndex : powerpillIndices) {
            for (DataTime dataTime : ghostIndices.values()) {
                int position = dataTime.value;
                if (position != lairIndex) {
                    int newCycleLength = cycleLength(powerPillIndex);
                    int newDistance;
                    newDistance = game.getShortestPath(position, powerPillIndex).length;

                    if (newCycleLength < currentCycleLength ||
                            ((newCycleLength == currentCycleLength) && newDistance < currentDistance)) {
                        currentCycleLength = newCycleLength;
                        currentDistance = newDistance;
                    }
                }
            }
        }
        return currentDistance;
    }

    @Override
    public Integer getDistanceToPowerpillWithShortestCycleNearestToGivenPosition(int position) {
	    LinkedList<Integer> powerpillIndices = getRemainingPowerPillsIndices();
        if (powerpillIndices.size() == 0) {
	        throw new ArrayStoreException();
        }
        int selectedPowerpill = powerpillIndices.getFirst();
	    int currentCycleLength = cycleLength(selectedPowerpill);
	    int currentDistance;
		currentDistance = Integer.MAX_VALUE;

        for (int powerPillIndex : powerpillIndices) {
            if (position != lairIndex) {
                int newCycleLength = cycleLength(powerPillIndex);
                int newDistance;
                newDistance = game.getShortestPath(position, powerPillIndex).length;

                if (newCycleLength < currentCycleLength ||
                        ((newCycleLength == currentCycleLength) && newDistance < currentDistance)) {
                    currentCycleLength = newCycleLength;
                    currentDistance = newDistance;
                }
            }
        }
        return currentDistance;
    }

    /** Długość najkrótszego cyklu przechodzącego przez dane pole. */
    private int cycleLength(int index) {
        LinkedList<Integer> neighbors = getNeighbors(index);
        if (neighbors.size() == 0) {
            return -1;
        }
        int neighbor = neighbors.getFirst();
        boolean horizontalNeighbor = (indexY(index) == indexY(neighbor));
        Constants.MOVE move;
        if (horizontalNeighbor) {
            move = Constants.MOVE.RIGHT;
        } else {
            move = Constants.MOVE.UP;
        }

        int[] pathTo = game.getShortestPath(index, neighbor, move);
        int[] pathFrom = game.getShortestPath(neighbor, index, move);

        return pathFrom.length + pathTo.length;
    }

    private HashSet<Integer> flooding(HashSet<Integer> pacmanInitialPositions, int steps,
                                      boolean excludeGhostPositions, boolean excludeObservable,
									  HashMap<GHOST, Integer> ghostPositions) {
        if (excludeObservable && !excludeGhostPositions) {
            throw new NotImplementedException();
        }
        if (ghostPositions == null) {
            ghostPositions = getGhostsPositions();
        }
        HashSet<Integer> newPositions = new HashSet<>();
        for (int i=0; i<steps; i++) {
            if (excludeGhostPositions) {
                for (int ghostPosition : ghostPositions.values()) {
                    LinkedList<Integer> ghostNeighborhood = getFreeNeighbors(ghostPosition);
                    ghostNeighborhood.add(ghostPosition);
                    for (int neighbor : ghostNeighborhood) {
                        if (pacmanInitialPositions.contains(neighbor)) {
                            pacmanInitialPositions.remove(neighbor);
                        }
                    }
                }
            }
            if (excludeObservable) {
                for (GHOST ghost : GHOST.values()) {
                    Set<Integer> visibleToGhost = positionsVisibleFromIndex(getGhostIndex(ghost).value);
                    for (int visiblePosition : visibleToGhost) {
                        if (pacmanInitialPositions.contains(visiblePosition)) {
                            pacmanInitialPositions.remove(visiblePosition);
                        }
                    }
                }
            }
            for (int position : pacmanInitialPositions) {
                for (int neighbor : getFreeNeighbors(position)) {
                    if (!pacmanInitialPositions.contains(neighbor) &&
                            (!excludeGhostPositions || !isGhostOrGhostsNeighbor(neighbor, ghostPositions)) &&
                            (!excludeObservable || !isNodeObservableBySomeGhost(neighbor))) {
                        newPositions.add(neighbor);
                    }
                }
            }
            for (int neighbor : getFreeNeighbors(getPacmanIndex().value)) {
                    if (!pacmanInitialPositions.contains(neighbor) &&
                            (!excludeGhostPositions || !isGhostOrGhostsNeighbor(neighbor, ghostPositions)) &&
                            (!excludeObservable || !isNodeObservableBySomeGhost(neighbor))) {
                        newPositions.add(neighbor);
                    }
                }
            pacmanInitialPositions.addAll(newPositions);
        }
        return pacmanInitialPositions;
	}

    /** Zalewanie ignorujące duszki, do przewidywania,
     *  co może zrobić pacman po zjedzeniu potężnej pigułki */
	@Override
    public HashSet<Integer> basicFlooding(HashSet<Integer> pacmanInitialPositions, int steps) {
        return flooding(pacmanInitialPositions, steps, false, false, null);
    }

	@Override
	public HashSet<Integer> basicFlooding(HashSet<Integer> pacmanInitialPositions) {
        return basicFlooding(pacmanInitialPositions, 1);
    }

    @Override
    public HashSet<Integer> basicFlooding(Integer pacmanInitialPosition) {
        HashSet<Integer> pacmanInitialPositions = new HashSet<>();
        pacmanInitialPositions.add(pacmanInitialPosition);
        return basicFlooding(pacmanInitialPositions);
    }

    @Override
    public HashSet<Integer> basicFlooding(Integer pacmanInitialPosition, int steps) {
        HashSet<Integer> pacmanInitialPositions = new HashSet<>();
        pacmanInitialPositions.add(pacmanInitialPosition);
        return basicFlooding(pacmanInitialPositions, steps);
    }

    /** Zalewanie zatrzymujące się jeśli napotka duszka, do przewidywania,
     *  co może zrobić pacman gdy duszki go łapią */
    @Override
    public HashSet<Integer> floodingStoppedByGhost(HashSet<Integer> pacmanInitialPositions, int steps, HashMap<GHOST, Integer> ghostPositions) {
        return flooding(pacmanInitialPositions, steps, true, false, ghostPositions);
    }

    @Override
    public HashSet<Integer> floodingStoppedByGhost(HashSet<Integer> pacmanInitialPositions, int steps) {
        return flooding(pacmanInitialPositions, steps, true, false, null);
    }

    @Override
    public HashSet<Integer> floodingStoppedByGhost(HashSet<Integer> pacmanInitialPositions) {
        return floodingStoppedByGhost(pacmanInitialPositions, 1);
    }

    @Override
    public HashSet<Integer> floodingStoppedByGhost(Integer pacmanInitialPosition) {
        return floodingStoppedByGhost(pacmanInitialPosition, 1);
    }

    @Override
    public HashSet<Integer> floodingStoppedByGhost(Integer pacmanInitialPosition, int steps) {
        HashSet<Integer> pacmanInitialPositions = new HashSet<>();
        pacmanInitialPositions.add(pacmanInitialPosition);
        return floodingStoppedByGhost(pacmanInitialPositions, steps);
    }

    /** Zalewanie z usuwaniem wszystkich pól, które widzi jakiś duszek */
    @Override
    public HashSet<Integer> floodingWithDeletionOnSight(HashSet<Integer> pacmanInitialPositions, int steps, HashMap<GHOST, Integer> ghostPositions) {
        return flooding(pacmanInitialPositions, steps, true, true, ghostPositions);
    }

    @Override
    public HashSet<Integer> floodingWithDeletionOnSight(HashSet<Integer> pacmanInitialPositions, int steps) {
        return flooding(pacmanInitialPositions, steps, true, true, null);
    }
    
    @Override
    public HashSet<Integer> floodingWithDeletionOnSight(HashSet<Integer> pacmanInitialPositions) {
        return floodingWithDeletionOnSight(pacmanInitialPositions, 1);
    }

    @Override
    public HashSet<Integer> floodingWithDeletionOnSight(Integer pacmanInitialPosition) {
        return floodingWithDeletionOnSight(pacmanInitialPosition, 1);
    }

    @Override
    public HashSet<Integer> floodingWithDeletionOnSight(Integer pacmanInitialPosition, int steps) {
        HashSet<Integer> pacmanInitialPositions = new HashSet<>();
        pacmanInitialPositions.add(pacmanInitialPosition);
        return floodingWithDeletionOnSight(pacmanInitialPositions, steps);
    }

    @Override
    public double unnormalizedProbabilityOfPacmanAtPosition(HashSet<Integer> positions, int initialPosition,
                                                            int position, int floodingTime) {
        if (positions.contains(position)) {
            switch (PROBABILITY_IN_USE) {
                case UNIFORM:
                    return 1.0;
                case TRIANGULAR_WITH_MANHATTAN_DISTANCE:
                    return -(1. - MIN_PROBABILITY) * game.getManhattanDistance(initialPosition, position) / floodingTime + 1;
                case TRIANGULAR_WITH_REAL_DISTANCE:
                    return -(1. - MIN_PROBABILITY) * game.getShortestPathDistance(initialPosition, position) / floodingTime + 1;
            }
        }
        return 0.0;
    }

    /** Prawdopodobieństwo znalezienia pacmana na którejś pozycji z selectedPositions */
    @Override
    public double normalizedProbabilityOfSelectedPositions(HashSet<Integer> positions, HashSet<Integer> selectedPositions,
                                                           int initialPosition, int floodingTime, int myPosition) {
        double unnormalizedProbability = 0;
        double normalizationDivident = 0;
        if (PROBABILITY_IN_USE == PROBABILITY.UNIFORM) {
            normalizationDivident = positions.size();
        } else {
            for (int position : positions) {
                normalizationDivident += unnormalizedProbabilityOfPacmanAtPosition(positions, initialPosition,
                        position, floodingTime);
            }
        }
        for (int position : selectedPositions) {
            unnormalizedProbability += unnormalizedProbabilityOfPacmanAtPosition(positions, initialPosition,
                    position, floodingTime) / (sqrt(game.getManhattanDistance(position, myPosition)) + 1.);
        }
        return unnormalizedProbability / normalizationDivident;
    }

    @Override
    public double normalizedProbabilityOfPositionsVisibleFromIndex(HashSet<Integer> positions, int index,
                                                                   int initialPosition, int floodingTime) {
        HashSet<Integer> positionsVisibleFromIndex = positionsVisibleFromIndex(index);
        return normalizedProbabilityOfSelectedPositions(positions, positionsVisibleFromIndex,
                initialPosition, floodingTime, index);
    }

    /** Funkcja oceny w stanie Retreat (maksymalizujemy) */
    @Override
    public double retreatStateEvaluationFunction(HashSet<Integer> positions, int index,
                                                                   int initialPosition, int floodingTime) {
        return -1.0 * normalizedProbabilityOfPositionsVisibleFromIndex(positions, index, initialPosition, floodingTime);
        /* TODO: jeśli zje pigułkę wnioskowanie, która to była,
           TODO: być może zalewanie, aż nie będzie 0,
           TODO: być może liczenie zalanych przed zjedzeniem pigułki jeśli jest 0
          */
    }

    /** Funkcja oceny w stanie Searching (maksymalizujemy) */
    @Override
    public double searchingStateEvaluationFunction(HashSet<Integer> positions, int index,
                                                                   int initialPosition, int floodingTime) {
        return normalizedProbabilityOfPositionsVisibleFromIndex(positions, index, initialPosition, floodingTime);
        /* TODO: być może zalewanie, aż nie będzie 0
           TODO: Jaki tu chcemy rodzaj zalewania? Może połączenie dwóch?

           TODO: FUNKCJA POWINNA ZWRACAĆ PRAWD. ELEMENTÓW USUNIĘTYCH PRZEZ ZOBACZENIE
          */
    }

    @Override
    public HashSet<Integer> positionsVisibleFromIndex(int index) {
        HashSet<Integer> positionsVisibleFromIndex = new HashSet<>();
        positionsVisibleFromIndex.add(index);
        for (Constants.MOVE direction : Constants.MOVE.values()) {
            int currentPosition = index;
            while (currentPosition >= 0 && !isWall(currentPosition)) {
                positionsVisibleFromIndex.add(currentPosition);
                currentPosition = game.getNeighbour(currentPosition, direction);
            }
        }
        return positionsVisibleFromIndex;
    }

    /** Funkcja oceny w stanie Catching (maksymalizujemy) */
    @Override
    public double catchingStateEvaluationFunction(HashSet<Integer> floodedPositios, double initialNumberOfFloodedPositions,
                                        int depthInTree, int lastPacmanPosition, int myPosition) {
        int numberOfFloodedPositions = numberOfFloodedPositions(floodedPositios);
        double evaluation = -1.0 * (initialNumberOfFloodedPositions +
                (double)(numberOfFloodedPositions - initialNumberOfFloodedPositions) / (depthInTree + 1));
        return evaluation - sqrt(game.getManhattanDistance(myPosition, lastPacmanPosition)) / 1000;
    }

    @Override
    public int numberOfFloodedPositions(HashSet<Integer> floodedPositios) {
        int numberOfFloodedPositions = floodedPositios.size();
        for (int powerpill : getRemainingPowerPillsIndices()) {
            if (floodedPositios.contains(powerpill)) {
                numberOfFloodedPositions += 10;
            }
        }
        return numberOfFloodedPositions;
    }

    @Override
    public int numberOfFloodedPositions() {
        return numberOfFloodedPositions(possiblePacmanPositions);
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

	private void setNodeBoardElement(int x, int y, Node value) {
		nodeBoard[y][x] = value;
	}

	private void setNodeBoardElement(int index, Node value) {
		setNodeBoardElement(indexX(index), indexY(index), value);
	}

	private char getNodeBoardElement(int x, int y) {
		return board[y][x];
	}

	private char getNodeBoardElement(int index) {
		return getNodeBoardElement(indexX(index), indexY(index));
	}

	private int indexX(int index) {
		return nodes[index].x;
	}

	private int indexY(int index) {
		return nodes[index].y;
	}

	private int index(int x, int y) {
	    return nodeBoard[y][x].nodeIndex;
    }


	public SmartMessenger getSmartMessenger() {
		return smartMessanger;
	}


	public Game getGame() {
		return game;
	}
	
	public GHOST getClientGhost() {
		return clientGhost;
	}
	
	public List<Integer> getClientIndicesHistory() {
		return clientIndicesHistory;
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
		for (int possiblePosition : possiblePacmanPositions) {
		    if (isEmpty(possiblePosition)) {
                setBoardElement(possiblePosition, floodedChar);
            }
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

	@Override
    public Integer getFloodingTime() {
        return floodingTime;
    }

    @Override
    public HashSet<Integer> getPossiblePacmanPositions() {
	    return possiblePacmanPositions;
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