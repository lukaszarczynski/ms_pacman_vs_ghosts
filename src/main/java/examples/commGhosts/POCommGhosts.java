package examples.commGhosts;

import entrants.BoardData;
import pacman.controllers.IndividualGhostController;
import pacman.controllers.MASController;
import pacman.game.Constants;
import pacman.game.Game;
import pacman.game.comms.BasicMessage;
import pacman.game.comms.Message;
import pacman.game.comms.Messenger;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.Random;


/**
 * Created by pwillic on 25/02/2016.
 */
public class POCommGhosts extends MASController {

    public POCommGhosts() {
        this(50);
    }

    public POCommGhosts(int TICK_THRESHOLD) {
        super(true, new EnumMap<Constants.GHOST, IndividualGhostController>(Constants.GHOST.class));
        controllers.put(Constants.GHOST.BLINKY, new POCommGhost(Constants.GHOST.BLINKY, TICK_THRESHOLD));
        controllers.put(Constants.GHOST.INKY, new GuardingGhost(Constants.GHOST.INKY));
        controllers.put(Constants.GHOST.PINKY, new GuardingGhost(Constants.GHOST.PINKY));
        controllers.put(Constants.GHOST.SUE, new GuardingGhost(Constants.GHOST.SUE));
    }

}

/** Duszek chodzi po najmniejszym cyklu dookoła zadanego pola
 *  Jest też odporny na losowe zmiany kierunku */
class GuardingGhost extends IndividualGhostController {
    BoardData boardData;
    boolean initialMoveMade;
    int powerpillToRemove;
    Boolean STATES_NOT_IMPLEMENTED = true;

    public GuardingGhost(Constants.GHOST ghost) {
        super(ghost);
        boardData = new BoardData();
        initialMoveMade = false;
    }

    @Override
    public Constants.MOVE getMove(Game game, long timeDue) {
        boardData.update(game);

        if (game.wasGhostEaten(ghost) || game.wasPacManEaten()){
            initialMoveMade = false;
        }

        powerpillToRemove = -1;
        for (int powerpillIndex : boardData.getRemainingPowerPillsIndices()) {
            if (game.isNodeObservable(powerpillIndex) &&
                    !game.isPowerPillStillAvailable(game.getPowerPillIndex(powerpillIndex))) {
                powerpillToRemove = powerpillIndex;
                int a = 1;
            }
        }



        Boolean requiresAction = game.doesGhostRequireAction(ghost);
        if (requiresAction != null && requiresAction)
        {
            int myPosition = game.getGhostCurrentNodeIndex(this.ghost);
            Constants.MOVE lastMove = game.getGhostLastMoveMade(this.ghost);

            int selectedPowerpill;
            if (STATES_NOT_IMPLEMENTED && boardData.getRemainingPowerPillsIndices().size() == 0) {
                selectedPowerpill = game.getPowerPillIndices()[0];
            } else {
                selectedPowerpill = boardData.getShortestCycleWithPowerpill(
                        boardData.getRemainingPowerPillsIndices(), myPosition, lastMove, initialMoveMade);
            }
            System.out.println(String.format("Go to superpill %d", selectedPowerpill));

            Constants.MOVE move = boardData.nextMoveTowardsTarget(myPosition, selectedPowerpill, lastMove);

            initialMoveMade = true;
            return move;
        }
        return Constants.MOVE.NEUTRAL;
    }
}

class POCommGhost extends IndividualGhostController {
    private final static float CONSISTENCY = 0.9f;    //attack Ms Pac-Man with this probability
    private final static int PILL_PROXIMITY = 15;        //if Ms Pac-Man is this close to a power pill, back away
    Random rnd = new Random();
    private int TICK_THRESHOLD;
    private int lastPacmanIndex = -1;
    private int tickSeen = -1;

    public POCommGhost(Constants.GHOST ghost) {
        this(ghost, 5);
    }

    public POCommGhost(Constants.GHOST ghost, int TICK_THRESHOLD) {
        super(ghost);
        this.TICK_THRESHOLD = TICK_THRESHOLD;
    }

    @Override
    public Constants.MOVE getMove(Game game, long timeDue) {
        // Housekeeping - throw out old info
        int currentTick = game.getCurrentLevelTime();
        if (currentTick <= 2 || currentTick - tickSeen >= TICK_THRESHOLD) {
            lastPacmanIndex = -1;
            tickSeen = -1;
        }

        // Can we see PacMan? If so tell people and update our info
        int pacmanIndex = game.getPacmanCurrentNodeIndex();
        int currentIndex = game.getGhostCurrentNodeIndex(ghost);
        Messenger messenger = game.getMessenger();
        if (pacmanIndex != -1) {
            lastPacmanIndex = pacmanIndex;
            tickSeen = game.getCurrentLevelTime();
            if (messenger != null) {
                messenger.addMessage(new BasicMessage(ghost, null, BasicMessage.MessageType.PACMAN_SEEN, pacmanIndex, game.getCurrentLevelTime()));
            }
        }

        // Has anybody else seen PacMan if we haven't?
        if (pacmanIndex == -1 && game.getMessenger() != null) {
            for (Message message : messenger.getMessages(ghost)) {
                if (message.getType() == BasicMessage.MessageType.PACMAN_SEEN) {
                    if (message.getTick() > tickSeen && message.getTick() < currentTick) { // Only if it is newer information
                        lastPacmanIndex = message.getData();
                        tickSeen = message.getTick();
                    }
                }
            }
        }
        if (pacmanIndex == -1) {
            pacmanIndex = lastPacmanIndex;
        }

        Boolean requiresAction = game.doesGhostRequireAction(ghost);
        if (requiresAction != null && requiresAction)        //if ghost requires an action
        {
            if (pacmanIndex != -1) {
                if (game.getGhostEdibleTime(ghost) > 0 || closeToPower(game))    //retreat from Ms Pac-Man if edible or if Ms Pac-Man is close to power pill
                {
                    try {
                        return game.getApproximateNextMoveAwayFromTarget(game.getGhostCurrentNodeIndex(ghost),
                                game.getPacmanCurrentNodeIndex(), game.getGhostLastMoveMade(ghost), Constants.DM.PATH);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.out.println(e);
                        System.out.println(pacmanIndex + " : " + currentIndex);
                    }
                } else {
                    if (rnd.nextFloat() < CONSISTENCY) {            //attack Ms Pac-Man otherwise (with certain probability)
                        try {
                            Constants.MOVE move = game.getApproximateNextMoveTowardsTarget(game.getGhostCurrentNodeIndex(ghost),
                                    pacmanIndex, game.getGhostLastMoveMade(ghost), Constants.DM.PATH);
                            return move;
                        } catch (ArrayIndexOutOfBoundsException e) {
                            System.out.println(e);
                            System.out.println(pacmanIndex + " : " + currentIndex);
                        }
                    }
                }
            } else {
                Constants.MOVE[] possibleMoves = game.getPossibleMoves(game.getGhostCurrentNodeIndex(ghost), game.getGhostLastMoveMade(ghost));
                return possibleMoves[rnd.nextInt(possibleMoves.length)];
            }
        }
        return null;
    }

    //This helper function checks if Ms Pac-Man is close to an available power pill
    private boolean closeToPower(Game game) {
        int[] powerPills = game.getPowerPillIndices();

        for (int i = 0; i < powerPills.length; i++) {
            Boolean powerPillStillAvailable = game.isPowerPillStillAvailable(i);
            int pacmanNodeIndex = game.getPacmanCurrentNodeIndex();
            if (pacmanNodeIndex == -1) {
                pacmanNodeIndex = lastPacmanIndex;
            }
            if (powerPillStillAvailable == null || pacmanNodeIndex == -1) {
                return false;
            }
            if (powerPillStillAvailable && game.getShortestPathDistance(powerPills[i], pacmanNodeIndex) < PILL_PROXIMITY) {
                return true;
            }
        }

        return false;
    }
}