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

abstract class State {
    public abstract String toString();
    //* Wybieranie ruchu w danym stanie */
    public abstract Constants.MOVE getMove(Game game, BoardData boardData);

    //* Funkcja przejścia używana przez duszka do określenia swojego stanu */
    public abstract State transitionFunction(Game game, BoardData boardData, HashMap<Constants.GHOST, State> ghostStates);

    //* Funkcja przejścia używana do określenia stanu innych */
    public abstract State transitionFunction(Game game, BoardData boardData,
                                             HashMap<Constants.GHOST, State> ghostStates, Constants.GHOST anotherGhost);

    public Constants.MOVE Handle(GhostContext context, Constants.GHOST ghost, Game game, BoardData boardData,
                                 HashMap<Constants.GHOST, State> ghostStates, long timeDue) {
        State stateAfterTransition = transitionFunction(game, boardData, ghostStates);
        if (stateAfterTransition != this) {
            context.state = stateAfterTransition;
            ghostStates.put(ghost, stateAfterTransition);
            return null;
        }

        return getMove(game, boardData);
    }
}

class RetreatState extends State {
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
                Constants.MOVE[] possibleMoves = game.getPossibleMoves(
                        game.getGhostCurrentNodeIndex(ghost), game.getGhostLastMoveMade(ghost));
                return possibleMoves[rand.nextInt(possibleMoves.length)];
            }
        }
        return Constants.MOVE.NEUTRAL;
    }

    @Override
    public State transitionFunction(Game game, BoardData boardData, HashMap<Constants.GHOST, State> ghostStates) {
        return transitionFunction(game, boardData, ghostStates, ghost);
    }

    @Override
    public State transitionFunction(Game game, BoardData boardData,
                                    HashMap<Constants.GHOST, State> ghostStates, Constants.GHOST anotherGhost) {
        if (game.wasPowerPillEaten() && startTime != game.getCurrentLevelTime()){
            return new RetreatState(ghost, game);
        }
        if (game.wasGhostEaten(anotherGhost) || (startTime + maxDuration) < game.getCurrentLevelTime() ||
                game.wasPacManEaten()){
            return new CatchingState(ghost);
        }
        return this;
    }

    @Override
    public String toString() {
        return "RetreatState";
    }
}

class CatchingState extends State {
    private static final int TICK_THRESHOLD = 50;
    private static final float CONSISTENCY = 0.9f;
    private static final int NONE_PILL_GUARDED_MAX_TIME = 5;
    private static final int MIN_LEVEL_TIME = 70;

    private Constants.GHOST ghost;
    private Random rand = new Random();
    private int lastPacmanIndex = -1;
    private int tickSeen = -1;
    private int nonePillGuardedTime = 0;

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
    public State transitionFunction(Game game, BoardData boardData, HashMap<Constants.GHOST, State> ghostStates) {
        if (game.wasPowerPillEaten() && game.getGhostCurrentNodeIndex(ghost) != boardData.getLairIndex()){
            return new RetreatState(ghost, game);
        }

        if (game.getGhostCurrentNodeIndex(ghost) != boardData.getLairIndex()) {
            boolean pillGuarded = false;
            for (State state : ghostStates.values()) {
                if (state instanceof GuardingState) {
                    pillGuarded = true;
                }
            }
            if (!pillGuarded && game.getCurrentLevelTime() > MIN_LEVEL_TIME) {
                nonePillGuardedTime++;
            } else {
                nonePillGuardedTime = 0;
            }
            int myPosition = game.getGhostCurrentNodeIndex(this.ghost);
            Constants.MOVE lastMove = game.getGhostLastMoveMade(this.ghost);
            if (boardData.getExactNumberOfPowerpills() > 0 && nonePillGuardedTime > NONE_PILL_GUARDED_MAX_TIME &&
                    game.getCurrentLevelTime() > MIN_LEVEL_TIME) {
                int selectedPowerpill = boardData.getPowerpillWithShortestCycle(myPosition, lastMove);
                if (Objects.equals(boardData.getDistanceToPowerpillWithShortestCycleNearestToGivenPosition(myPosition),
                        boardData.getDistanceToPowerpillWithShortestCycleNearestToSomeGhost())) {
                    boardData.getSmartMessenger().broadcastMessageIAmHeading(selectedPowerpill);
                    System.out.println(String.format("Go to superpill %d", selectedPowerpill));
                    return new GuardingState(ghost, game, selectedPowerpill);
                }
            }
        }

        return this;
    }

    @Override
    public State transitionFunction(Game game, BoardData boardData,
                                    HashMap<Constants.GHOST, State> ghostStates, Constants.GHOST anotherGhost) {
        if (game.wasPowerPillEaten()){
            return new RetreatState(ghost, game);
        }

        LinkedList<Integer> powerpills = boardData.getRemainingPowerPillsIndices();
        MessageList headingMessage = boardData.getSmartMessenger().getCurrentMessages().
                selectType(Message.MessageType.I_AM_HEADING).selectSender(anotherGhost);
        if (headingMessage.size() != 1) {
            return this;
        }
        int heading = headingMessage.get(0).getData();
        if (headingMessage.size() == 1 && powerpills.contains(heading)) {
            return new GuardingState(ghost, game, heading);
        }

        return this;
    }

    @Override
    public String toString() {
        return "CatchingState";
    }
}

/** Duszek chodzi po najmniejszym cyklu dookoła zadanego pola
 *  Jest też odporny na losowe zmiany kierunku */
class GuardingState extends State {
    private static final int INITIAL_POSITION = -1;
    private Constants.GHOST ghost;
    private Integer selectedPowerpill;
    private Integer myPosition;
    private Constants.MOVE lastMove;

    GuardingState(Constants.GHOST ghost, Game game, int selectedPowerpill) {
        this.ghost = ghost;
        this.selectedPowerpill = selectedPowerpill;
        myPosition = game.getGhostCurrentNodeIndex(this.ghost);
        lastMove = game.getGhostLastMoveMade(this.ghost);
    }

    @Override
    public Constants.MOVE getMove(Game game, BoardData boardData) {
        boardData.getSmartMessenger().broadcastMessageIAmHeading(selectedPowerpill);

        Boolean requiresAction = game.doesGhostRequireAction(ghost);
        if (requiresAction != null && requiresAction)
        {
            myPosition = game.getGhostCurrentNodeIndex(this.ghost);
            lastMove = game.getGhostLastMoveMade(this.ghost);


            Constants.MOVE move = boardData.nextMoveTowardsTarget(myPosition, selectedPowerpill, lastMove);

            return move;
        }
        return Constants.MOVE.NEUTRAL;
    }

    @Override
    public State transitionFunction(Game game, BoardData boardData, HashMap<Constants.GHOST, State> ghostStates) {
        if (game.wasPowerPillEaten() && game.getGhostCurrentNodeIndex(ghost) != boardData.getLairIndex()){
            return new RetreatState(ghost, game);
        }

        if (game.wasPacManEaten()) {
            return new CatchingState(ghost);
        }
        if (boardData.getExactNumberOfPowerpills() == 0 || boardData.isEmpty(selectedPowerpill)) {
            boardData.getSmartMessenger().broadcastMessageIAmHeading(BoardData.INITIAL_POSITION);
            return new CatchingState(ghost);
        }

        return this;
    }

    @Override
    public State transitionFunction(Game game, BoardData boardData,
                                    HashMap<Constants.GHOST, State> ghostStates, Constants.GHOST anotherGhost) {
        if (game.wasPowerPillEaten()){
            return new RetreatState(ghost, game);
        }

        MessageList headingMessages = boardData.getSmartMessenger().getCurrentMessages().
                selectType(Message.MessageType.I_AM_HEADING).selectSender(anotherGhost);
        if (headingMessages.size() >= 1) {
            for (Message message : headingMessages) {
                if (message.getData() == BoardData.INITIAL_POSITION) {
                    return new CatchingState(ghost);
                }
            }
        }

        return this;
    }

    @Override
    public String toString() {
        return "GuardingState";
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

        MessageList headingMessages = boardData.getSmartMessenger().getCurrentMessages().
                selectType(Message.MessageType.I_AM_HEADING);
        if (headingMessages.size() >= 1) {
            for (Message message : headingMessages) {
                 if (message.getData() == BoardData.INITIAL_POSITION) {
                     boardData.removePowerpill(headingMessages.get(0).getSender());
                 }
            }
        }

        for (Constants.GHOST anotherGhost : Constants.GHOST.values()) {
            if (anotherGhost == ghost) {
                State stateAfterTransition = ghostStates.get(anotherGhost).transitionFunction(
                        game, boardData, ghostStates);
                ghostStates.put(ghost, stateAfterTransition);
                state = stateAfterTransition;
            } else {
                State stateAfterTransition = ghostStates.get(anotherGhost).transitionFunction(
                        game, boardData, ghostStates, anotherGhost);
                ghostStates.put(anotherGhost, stateAfterTransition);
            }
        }

        Constants.MOVE move = null;
        while (move == null) {
            move = state.Handle(this, ghost, game, boardData, ghostStates, timeDue);
        }

        System.out.println(boardData.toString());

        return move;
    }
}

public class StateGhosts extends MASController {
    public StateGhosts() {
        super(true, new EnumMap<Constants.GHOST, IndividualGhostController>(Constants.GHOST.class));
        controllers.put(Constants.GHOST.BLINKY, new GhostContext(Constants.GHOST.BLINKY));
        controllers.put(Constants.GHOST.PINKY, new GhostContext(Constants.GHOST.PINKY));
        controllers.put(Constants.GHOST.INKY, new GhostContext(Constants.GHOST.INKY));
        controllers.put(Constants.GHOST.SUE, new GhostContext(Constants.GHOST.SUE));
    }
}
