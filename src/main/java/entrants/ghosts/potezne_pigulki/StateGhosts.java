package entrants.ghosts.potezne_pigulki;

import entrants.BoardData;
import entrants.MessageList;
import pacman.controllers.IndividualGhostController;
import pacman.controllers.MASController;
import pacman.game.Constants;
import pacman.game.Game;
import pacman.game.comms.BasicMessage;
import pacman.game.comms.Message;

import java.util.*;

interface State {
    Constants.MOVE Handle(GhostContext context, Game game, BoardData boardData, long timeDue);
    Constants.MOVE getMove(Game game, BoardData boardData);
    State transitionFunction(Game game);
    State transitionFunction(Game game, Constants.GHOST anotherGhost);
}

class RetreatState implements State {
    private static final int TICK_THRESHOLD = 50;

    private Constants.GHOST ghost;
    private Integer startTime;
    private Integer maxDuration;
    private Random rand = new Random();
    private int lastPacmanIndex = -1;
    private int tickSeen = -1;

    RetreatState(Constants.GHOST ghost, Game game)
    {
        this.ghost = ghost;
        startTime = game.getCurrentLevelTime();
        maxDuration = game.getGhostEdibleTime(ghost);
    }

    @Override
    public Constants.MOVE getMove(Game game, BoardData boardData) {
        Boolean requiresAction = game.doesGhostRequireAction(ghost);

        int currentTick = game.getCurrentLevelTime();
        if (currentTick <= 2 || currentTick - tickSeen >= TICK_THRESHOLD) {
            lastPacmanIndex = -1;
            tickSeen = -1;
        }

        int pacmanIndex = game.getPacmanCurrentNodeIndex();

        if (pacmanIndex == -1 && game.getMessenger() != null) {
            for (Message message : boardData.getSmartMessenger().getCurrentMessages()) {
                if (message.getType() == BasicMessage.MessageType.PACMAN_SEEN) {
                    if (message.getTick() > tickSeen && message.getTick() < currentTick) {
                        lastPacmanIndex = message.getData();
                        tickSeen = message.getTick();
                    }
                }
            }
            pacmanIndex = lastPacmanIndex;
        }

        if (requiresAction != null && requiresAction)
        {
            if (pacmanIndex != -1) {
                try {
                    return game.getApproximateNextMoveAwayFromTarget(game.getGhostCurrentNodeIndex(ghost),
                            game.getPacmanCurrentNodeIndex(), game.getGhostLastMoveMade(ghost), Constants.DM.PATH);
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.out.println(e);
                }
            } else {
                Constants.MOVE[] possibleMoves = game.getPossibleMoves(game.getGhostCurrentNodeIndex(ghost), game.getGhostLastMoveMade(ghost));
                return possibleMoves[rand.nextInt(possibleMoves.length)];
            }
        }
        return Constants.MOVE.NEUTRAL;
    }

    @Override
    public State transitionFunction(Game game) {
        return transitionFunction(game, ghost);
    }

    @Override
    public State transitionFunction(Game game, Constants.GHOST anotherGhost) {
        if (game.wasGhostEaten(anotherGhost) || (startTime + maxDuration) < game.getCurrentLevelTime()){
            return new CatchingState(ghost);
        }
        return this;
    }

    @Override
    public Constants.MOVE Handle(GhostContext context, Game game, BoardData boardData, long timeDue) {
        State stateAfterTransition = transitionFunction(game);
        if (stateAfterTransition != this) {
            context.state = stateAfterTransition;
            return null;
        }

        return getMove(game, boardData);
    }
}

class CatchingState implements State {
    private static final int TICK_THRESHOLD = 50;
    private static final float CONSISTENCY = 0.9f;

    private Constants.GHOST ghost;
    private Random rand = new Random();
    private int lastPacmanIndex = -1;
    private int tickSeen = -1;

    CatchingState(Constants.GHOST ghost)
    {
        this.ghost = ghost;
    }

    @Override
    public Constants.MOVE getMove(Game game, BoardData boardData) {
        Boolean requiresAction = game.doesGhostRequireAction(ghost);

        int currentTick = game.getCurrentLevelTime();
        if (currentTick <= 2 || currentTick - tickSeen >= TICK_THRESHOLD) {
            lastPacmanIndex = -1;
            tickSeen = -1;
        }

        int pacmanIndex = game.getPacmanCurrentNodeIndex();

        if (pacmanIndex == -1 && game.getMessenger() != null) {
            for (Message message : boardData.getSmartMessenger().getCurrentMessages()) {
                if (message.getType() == BasicMessage.MessageType.PACMAN_SEEN) {
                    if (message.getTick() > tickSeen && message.getTick() < currentTick) { // Only if it is newer information
                        lastPacmanIndex = message.getData();
                        tickSeen = message.getTick();
                    }
                }
            }
            pacmanIndex = lastPacmanIndex;
        }

        if (requiresAction != null && requiresAction)
        {
            if (pacmanIndex != -1) {
                {
                    if (rand.nextFloat() < CONSISTENCY) {            //attack Ms Pac-Man otherwise (with certain probability)
                        try {
                            Constants.MOVE move = game.getApproximateNextMoveTowardsTarget(game.getGhostCurrentNodeIndex(ghost),
                                    pacmanIndex, game.getGhostLastMoveMade(ghost), Constants.DM.PATH);
                            return move;
                        } catch (ArrayIndexOutOfBoundsException e) {
                            System.out.println(e);
                        }
                    }
                }
            } else {
                Constants.MOVE[] possibleMoves = game.getPossibleMoves(game.getGhostCurrentNodeIndex(ghost), game.getGhostLastMoveMade(ghost));
                return possibleMoves[rand.nextInt(possibleMoves.length)];
            }
        }
        return Constants.MOVE.NEUTRAL;
    }

    @Override
    public State transitionFunction(Game game) {
        return transitionFunction(game, ghost);
    }

    @Override
    public State transitionFunction(Game game, Constants.GHOST anotherGhost) {
        if (game.wasPowerPillEaten()){
            return new RetreatState(ghost, game);
        }
        return this;
    }

    @Override
    public Constants.MOVE Handle(GhostContext context, Game game, BoardData boardData, long timeDue) {
        State stateAfterTransition = transitionFunction(game);
        if (stateAfterTransition != this) {
            context.state = stateAfterTransition;
            return null;
        }

        return getMove(game, boardData);
    }
}

/** Duszek chodzi po najmniejszym cyklu dookoła zadanego pola
 *  Jest też odporny na losowe zmiany kierunku */
class GuardingGhost extends IndividualGhostController {
    private BoardData boardData;
    private boolean initialMoveMade;
    private Boolean STATES_NOT_IMPLEMENTED = true;

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

//        int powerpillToRemove = -1;
//        for (int powerpillIndex : boardData.getRemainingPowerPillsIndices()) {
//            if (game.isNodeObservable(powerpillIndex) &&
//                    !game.isPowerPillStillAvailable(game.getPowerPillIndex(powerpillIndex))) {
//                powerpillToRemove = powerpillIndex;
//            }
//        }

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

class GhostContext extends IndividualGhostController {
    State state;
    private HashMap<Constants.GHOST, State> ghostStates;
    private BoardData boardData;

    GhostContext(Constants.GHOST ghost) {
        super(ghost);
        ghostStates = new HashMap<>();
        boardData = new BoardData(ghost, true);
    }

    @Override
    public Constants.MOVE getMove(Game game, long timeDue) {
        if (state == null) {
            state = new CatchingState(ghost);
            for (Constants.GHOST anotherGhost : Constants.GHOST.values()) {
                ghostStates.put(anotherGhost, new CatchingState(ghost));
            }
        }

        boardData.update(game);

        if (ghost == Constants.GHOST.PINKY) {
            System.out.println(boardData.toString());
        }

        MessageList otherMessages = boardData.getSmartMessenger().getMessagesHistory().excludeType(Message.MessageType.I_AM);

        Constants.MOVE move = null;
        while (move == null) {
            move = state.Handle(this, game, boardData, timeDue);
        }

        for (Constants.GHOST anotherGhost : Constants.GHOST.values()) {
            State stateAfterTransition = ghostStates.get(anotherGhost).transitionFunction(game, anotherGhost);
            ghostStates.put(anotherGhost, stateAfterTransition);
        }

        return move;
    }
}

public class StateGhosts extends MASController {
    public StateGhosts() {
        super(true, new EnumMap<Constants.GHOST, IndividualGhostController>(Constants.GHOST.class));
        controllers.put(Constants.GHOST.BLINKY, new GhostContext(Constants.GHOST.BLINKY));
        controllers.put(Constants.GHOST.PINKY, new GuardingGhost(Constants.GHOST.PINKY));
        controllers.put(Constants.GHOST.INKY, new GhostContext(Constants.GHOST.INKY));
        controllers.put(Constants.GHOST.SUE, new GhostContext(Constants.GHOST.SUE));
    }
}
