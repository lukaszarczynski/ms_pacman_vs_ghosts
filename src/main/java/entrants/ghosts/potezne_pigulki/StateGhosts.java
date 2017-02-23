package entrants.ghosts.potezne_pigulki;

import entrants.BoardData;
import entrants.MessageList;
import pacman.controllers.IndividualGhostController;
import pacman.controllers.MASController;
import pacman.game.Constants;
import pacman.game.Game;
import pacman.game.comms.Message;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
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
        if (stateAfterTransition != context.state) {
            context.state = stateAfterTransition;
            ghostStates.put(ghost, stateAfterTransition);
            return null;
        }

        if (game.getCurrentLevelTime() < 50) {
            return Constants.MOVE.LEFT;
        }

        return getMove(game, boardData);
    }
}

class RetreatState extends State {
    private static final int TICK_THRESHOLD = 50;

    private Constants.GHOST ghost;
    private Integer startTime;
    private Integer maxDuration;

    RetreatState(Constants.GHOST ghost, Game game)
    {
        this.ghost = ghost;
        startTime = game.getCurrentLevelTime();
        maxDuration = game.getGhostEdibleTime(ghost);
    }

    @Override
    public Constants.MOVE getMove(Game game, BoardData boardData) {
        Boolean requiresAction = game.doesGhostRequireAction(ghost);

        if (requiresAction != null && requiresAction)
        {
            Constants.MOVE bestMove = Constants.MOVE.NEUTRAL;
            double bestScore;

            int currentGhostIndex = game.getGhostCurrentNodeIndex(ghost);
            int lastSeenPacmanPosition = boardData.getPacmanIndexValue();
            Constants.MOVE lastMove = game.getGhostLastMoveMade(ghost);

            int floodingTime = boardData.getFloodingTime();
            int newFloodingTime = floodingTime;

            boolean allScoresEqual = true;

            HashMap<Constants.MOVE, HashSet<Integer>> newFloodedPositions = new HashMap<>();

            for (Constants.MOVE move : game.getPossibleMoves(currentGhostIndex, lastMove)) {
                newFloodedPositions.put(move, new HashSet<>(boardData.getPossiblePacmanPositions()));
            }

            for (int i=0; i<100 && allScoresEqual; i++) {
                allScoresEqual = true;
                HashSet<Double> scores = new HashSet<>();
                bestMove = Constants.MOVE.NEUTRAL;
                bestScore = Double.NEGATIVE_INFINITY;

                newFloodingTime++;

                for (Constants.MOVE move : game.getPossibleMoves(currentGhostIndex, lastMove)) {
                    int myNewPosition = game.getNeighbour(currentGhostIndex, move);

                    newFloodedPositions.put(move, boardData.basicFlooding(newFloodedPositions.get(move)));

                    double score = boardData.retreatStateEvaluationFunction(newFloodedPositions.get(move), myNewPosition,
                            lastSeenPacmanPosition, newFloodingTime);
                    scores.add(score);
                    if (scores.size() > 1) {
                        allScoresEqual = false;
                    }

                    if (score > bestScore) {
                        bestMove = move;
                        bestScore = score;
                    }
                }
            }
            return bestMove;
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

class CatchingStateGameTree {
    DefaultTreeModel tree;
    private Random rand = new Random();

    private Game game;
    private BoardData boardData;
    private Constants.GHOST currentGhost;
    private int lastSeenPacmanPosition;
    private double initialNumberOfFloodedPositions;

    private HashSet<DefaultMutableTreeNode> leafs = new HashSet<>();

    CatchingStateGameTree(Game game, BoardData boardData, Constants.GHOST currentGhost, int lastSeenPacmanPosition,
                          HashMap<Constants.GHOST, Integer> currentGhostPositions,
                          EnumMap<Constants.GHOST, Constants.MOVE> ghostLastMoves,
                          HashSet<Integer> floodedPositions) {
        this.game = game;
        this.boardData = boardData;
        this.currentGhost = currentGhost;
        this.lastSeenPacmanPosition = lastSeenPacmanPosition;
        initialNumberOfFloodedPositions = boardData.numberOfFloodedPositions();
        DefaultMutableTreeNode root = createRoot(currentGhostPositions, ghostLastMoves, floodedPositions);
        tree = new DefaultTreeModel(root);
        leafs.add(root);
    }

    CatchingStateGameTree(Game game, BoardData boardData, Constants.GHOST currentGhost, int lastSeenPacmanPosition) {
        this.game = game;
        this.boardData = boardData;
        this.currentGhost = currentGhost;
        this.lastSeenPacmanPosition = lastSeenPacmanPosition;
        DefaultMutableTreeNode root = createRoot();
        tree = new DefaultTreeModel(root);
        leafs.add(root);
    }

    private DefaultMutableTreeNode createRoot() {
        HashMap<Constants.GHOST, Integer> currentGhostPositions = boardData.getGhostsPositions();
        EnumMap<Constants.GHOST, Constants.MOVE> ghostLastMoves = boardData.getGhostsDirections();
        HashSet<Integer> floodedPositions = boardData.getPossiblePacmanPositions();
        return new DefaultMutableTreeNode(new CatchingStateNodeData(0, currentGhost,
                currentGhostPositions, ghostLastMoves, floodedPositions));
    }

    private DefaultMutableTreeNode createRoot(HashMap<Constants.GHOST, Integer> currentGhostPositions,
                                              EnumMap<Constants.GHOST, Constants.MOVE> ghostLastMoves,
                                              HashSet<Integer> floodedPositions) {
        return new DefaultMutableTreeNode(new CatchingStateNodeData(0, currentGhost,
                currentGhostPositions, ghostLastMoves, floodedPositions));
    }

    private DefaultMutableTreeNode getBestLeaf() {
        DefaultMutableTreeNode bestLeaf = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (DefaultMutableTreeNode leaf : leafs) {
            double score = ((CatchingStateNodeData)(leaf.getUserObject())).score;
            if (score + (rand.nextDouble() / 10) > bestScore + (rand.nextDouble() / 10)) {
                bestLeaf = leaf;
                bestScore = score;
            }
        }
        return bestLeaf;
    }

    double getBestLeafScore() {
        return ((CatchingStateNodeData)(getBestLeaf().getUserObject())).score;
    }

    Constants.MOVE getBestLeafDirection() {
        return ((CatchingStateNodeData)(getBestLeaf().getUserObject())).moveFromParent();
    }

    void createChildren(DefaultMutableTreeNode node) {
        if (node.isLeaf()) {
            Constants.MOVE ghostMove;
            int ghostPosition;

            CatchingStateNodeData parentData = (CatchingStateNodeData)(node.getUserObject());
            for (Constants.MOVE direction : game.getPossibleMoves(parentData.myPosition(), parentData.moveFromParent())) {

                EnumMap<Constants.GHOST, Constants.MOVE> childGhostLastMoves =
                        new EnumMap<>(Constants.GHOST.class);
                HashMap<Constants.GHOST, Integer> childGhostPositions = new HashMap<>();

                ghostPosition = game.getNeighbour(parentData.currentGhostPositions.get(currentGhost), direction);
                childGhostLastMoves.put(currentGhost, direction);
                childGhostPositions.put(currentGhost, ghostPosition);

                for (Constants.GHOST ghost : Constants.GHOST.values()) {
                    if (ghost == currentGhost) {
                        ghostMove = direction;
                    } else {
                        ghostMove = getApproximateGhostMove(parentData.currentGhostPositions.get(ghost),
                                parentData.ghostLastMoves.get(ghost), ghost,
                                parentData.currentGhostPositions, parentData.ghostLastMoves, parentData.floodedPositions,
                                childGhostLastMoves.get(currentGhost), childGhostPositions.get(currentGhost));
                    }

                    if (parentData.ghostLastMoves.get(ghost) == Constants.MOVE.NEUTRAL) {
                        ghostPosition = parentData.currentGhostPositions.get(ghost);
                    } else {
                        ghostPosition = game.getNeighbour(parentData.currentGhostPositions.get(ghost), ghostMove);
                    }

                    childGhostLastMoves.put(ghost, ghostMove);
                    childGhostPositions.put(ghost, ghostPosition);
                }
                CatchingStateNodeData childData = new CatchingStateNodeData(parentData.depth + 1, currentGhost,
                        childGhostPositions, childGhostLastMoves, parentData.floodedPositions);

                childData.floodedPositions = oneStepFlooding(childData, parentData);

                childData.score = evaluate(childData);

                DefaultMutableTreeNode child = new DefaultMutableTreeNode(childData);
                node.add(child);
                leafs.add(child);
            }
            leafs.remove(node);
        }
    }

    private Constants.MOVE getApproximateGhostMove(int previousGhostPosition, Constants.MOVE previousGhostMove,
                                                   Constants.GHOST ghost,
                                                   HashMap<Constants.GHOST, Integer> currentGhostPositions,
                                                   EnumMap<Constants.GHOST, Constants.MOVE> ghostLastMoves,
                                                   HashSet<Integer> floodedPositions,
                                                   Constants.MOVE callerDirection, Integer callerPosition) {
        if (previousGhostMove == Constants.MOVE.NEUTRAL) {
            return Constants.MOVE.NEUTRAL;
        }

        HashMap<Constants.GHOST, Integer> ghostPositions = new HashMap<>(currentGhostPositions);
        EnumMap<Constants.GHOST, Constants.MOVE> ghostMoves = new EnumMap<>(ghostLastMoves);
        ghostPositions.put(currentGhost, callerPosition);
        ghostMoves.put(currentGhost, callerDirection);

        int[] possibleMoves = game.getNeighbouringNodes(previousGhostPosition, previousGhostMove);
        if (possibleMoves.length == 1) {
            return game.getMoveToMakeToReachDirectNeighbour(previousGhostPosition, possibleMoves[0]);
        }
        else {
            CatchingStateGameTree tempTree = new CatchingStateGameTree(game, boardData, ghost, lastSeenPacmanPosition,
                    ghostPositions, ghostMoves, floodedPositions);
            tempTree.createChildren((DefaultMutableTreeNode) (tempTree.tree.getRoot()));
            Constants.MOVE bestAnotherGhostMove = tempTree.getBestLeafDirection();
            return bestAnotherGhostMove;
        }
    }

    private HashSet<Integer> oneStepFlooding(CatchingStateNodeData childData, CatchingStateNodeData parentData) {
        return boardData.floodingStoppedByGhost(parentData.floodedPositions, 1, childData.currentGhostPositions);
    }


    private double evaluate(CatchingStateNodeData childData) {
        return boardData.catchingStateEvaluationFunction(childData.floodedPositions,
                initialNumberOfFloodedPositions, childData.depth,
                lastSeenPacmanPosition, childData.myPosition());
    }

    void searchToGivenDepth(DefaultMutableTreeNode node, int maxDepth) {
        if (maxDepth > 0) {
            createChildren(node);
            for (int i = 0; i < node.getChildCount(); i++) {
                searchToGivenDepth((DefaultMutableTreeNode)(node.getChildAt(i)), maxDepth - 1);
            }
        }
    }

    void DeepSearch(DefaultMutableTreeNode node, int iterationDepth, int numberOfIterations) {
        searchToGivenDepth(node, iterationDepth);
        for (int i = 0; i < numberOfIterations; i++) {
            DefaultMutableTreeNode bestLeaf = getBestLeaf();
            searchToGivenDepth(bestLeaf, iterationDepth);
        }
    }
}

class CatchingStateNodeData {
    private Constants.GHOST currentGhost;
    Double score = Double.NEGATIVE_INFINITY;
    HashMap<Constants.GHOST, Integer> currentGhostPositions;
    EnumMap<Constants.GHOST, Constants.MOVE> ghostLastMoves;
    Integer depth;
    HashSet<Integer> floodedPositions;

    Integer myPosition() {
        return currentGhostPositions.get(currentGhost);
    }

    Constants.MOVE moveFromParent() {
        return ghostLastMoves.get(currentGhost);
    }


    CatchingStateNodeData(Integer depth, Constants.GHOST currentGhost,
                          HashMap<Constants.GHOST, Integer> currentGhostPositions,
                          EnumMap<Constants.GHOST, Constants.MOVE> ghostLastMoves,
                          HashSet<Integer> floodedPositions) {
        this.depth = depth;
        this.currentGhost = currentGhost;
        this.currentGhostPositions = currentGhostPositions;
        this.ghostLastMoves = ghostLastMoves;
        this.floodedPositions = floodedPositions;
    }
}

class CatchingState extends State {
    private static final int NONE_PILL_GUARDED_MAX_TIME = 5;
    private static final int MIN_LEVEL_TIME = 50;

    private Constants.GHOST ghost;
    private int nonePillGuardedTime = 0;

    CatchingState(Constants.GHOST ghost)
    {
        this.ghost = ghost;
    }

    public static Constants.MOVE getBestMoveWithIteratedFlooding(
            Game game, BoardData boardData, Constants.GHOST ghost, int lastSeenPacmanPosition,
            Constants.MOVE lastMove, HashMap<Constants.GHOST, Integer> currentGhostPositions,
            HashMap<Constants.MOVE, HashSet<Integer>> newFloodedPositions,
            int maxNumberOfIterations) {
        boolean allScoresEqual = true;
        Constants.MOVE bestMove = null;
        double bestScore;
        int initialNumberOfFloodedPositions;
        for (int i = 0; i < maxNumberOfIterations && allScoresEqual; i++) {
            allScoresEqual = true;
            HashSet<Double> scores = new HashSet<>();
            bestMove = Constants.MOVE.NEUTRAL;
            bestScore = Double.NEGATIVE_INFINITY;
            initialNumberOfFloodedPositions = boardData.numberOfFloodedPositions();

            for (Constants.MOVE move : game.getPossibleMoves(currentGhostPositions.get(ghost), lastMove)) {
                HashMap<Constants.GHOST, Integer> ghostPositions = boardData.getGhostsPositions();
                int myNewPosition = game.getNeighbour(currentGhostPositions.get(ghost), move);
                ghostPositions.put(ghost, myNewPosition);

                newFloodedPositions.put(move, boardData.floodingStoppedByGhost(newFloodedPositions.get(move),
                        1, ghostPositions));

                double score = boardData.catchingStateEvaluationFunction(newFloodedPositions.get(move),
                        initialNumberOfFloodedPositions, i, lastSeenPacmanPosition, myNewPosition);

                scores.add(score);
                if (scores.size() > 1) {
                    allScoresEqual = false;
                }

                if (score > bestScore) {
                    bestMove = move;
                    bestScore = score;
                }
            }
        }
        return bestMove;
    }

    @Override
    public Constants.MOVE getMove(Game game, BoardData boardData) {
        Boolean requiresAction = game.doesGhostRequireAction(ghost);

        if (requiresAction != null && requiresAction)
        {
//            Constants.MOVE bestMove;

            int lastSeenPacmanPosition = boardData.getPacmanIndexValue();

//            HashMap<Constants.GHOST, Integer> currentGhostPositions = boardData.getGhostsPositions();
//            EnumMap<Constants.GHOST, Constants.MOVE> ghostDirections = boardData.getGhostsDirections();

//            Constants.MOVE lastMove = ghostDirections.get(ghost);

//            HashMap<Constants.MOVE, HashSet<Integer>> newFloodedPositions = new HashMap<>();
//
//            /** Dla każdego możliwego ruchu mojego duszka przygotuj słownik z polami, które zaleje */
//            for (Constants.MOVE move : game.getPossibleMoves(currentGhostPositions.get(ghost), lastMove)) {
//                newFloodedPositions.put(move, new HashSet<>(boardData.getPossiblePacmanPositions()));
//            }

//            bestMove = CatchingState.getBestMoveWithIteratedFlooding(game, boardData, ghost, lastSeenPacmanPosition,
//                    lastMove, currentGhostPositions, newFloodedPositions, 1); // TODO: zamienić na 100

            CatchingStateGameTree gameTree = new CatchingStateGameTree(game, boardData, ghost, lastSeenPacmanPosition);

//            gameTree.createChildren((DefaultMutableTreeNode) (gameTree.tree.getRoot()));

            gameTree.searchToGivenDepth((DefaultMutableTreeNode) (gameTree.tree.getRoot()), 25);

//            gameTree.DeepSearch((DefaultMutableTreeNode) (gameTree.tree.getRoot()), 25, 3);

            double bestLeafScore = gameTree.getBestLeafScore();

            return gameTree.getBestLeafDirection();
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

        if (boardData.numberOfFloodedPositions() > 200) {
            return new SearchingState(ghost);
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

        if (boardData.numberOfFloodedPositions() > 200) {
            return new CatchingState(ghost);
        }

        return this;
    }

    @Override
    public String toString() {
        return "CatchingState";
    }
}

class SearchingState extends State {
    private static final int TICK_THRESHOLD = 50;
    private static final float CONSISTENCY = 0.9f;
    private static final int NONE_PILL_GUARDED_MAX_TIME = 5;
    private static final int MIN_LEVEL_TIME = 50;

    private Constants.GHOST ghost;
    private Random rand = new Random();
    private int lastPacmanIndex = -1;
    private int tickSeen = -1;
    private int nonePillGuardedTime = 0;

    SearchingState(Constants.GHOST ghost)
    {
        this.ghost = ghost;
    }

    @Override
    public Constants.MOVE getMove(Game game, BoardData boardData) {
        Boolean requiresAction = game.doesGhostRequireAction(ghost);

        if (requiresAction != null && requiresAction)
        {
            Constants.MOVE bestMove = Constants.MOVE.NEUTRAL;
            double bestScore;

            int currentGhostIndex = game.getGhostCurrentNodeIndex(ghost);
            int lastSeenPacmanPosition = boardData.getPacmanIndexValue();
            Constants.MOVE lastMove = game.getGhostLastMoveMade(ghost);

            int floodingTime = boardData.getFloodingTime();
            int newFloodingTime = floodingTime;

            boolean allScoresEqual = true;

            HashMap<Constants.MOVE, HashSet<Integer>> newFloodedPositions = new HashMap<>();

            for (Constants.MOVE move : game.getPossibleMoves(currentGhostIndex, lastMove)) {
                newFloodedPositions.put(move, new HashSet<>(boardData.getPossiblePacmanPositions()));
            }

            for (int i=0; i<100 && allScoresEqual; i++) {
                allScoresEqual = true;
                HashSet<Double> scores = new HashSet<>();
                bestMove = Constants.MOVE.NEUTRAL;
                bestScore = Double.NEGATIVE_INFINITY;

                newFloodingTime++;

                for (Constants.MOVE move : game.getPossibleMoves(currentGhostIndex, lastMove)) {
                    int myNewPosition = game.getNeighbour(currentGhostIndex, move);
                    newFloodedPositions.put(move, boardData.basicFlooding(newFloodedPositions.get(move)));

                    double score = boardData.searchingStateEvaluationFunction(newFloodedPositions.get(move), myNewPosition,
                            lastSeenPacmanPosition, newFloodingTime);

                    scores.add(score);
                    if (scores.size() > 1) {
                        allScoresEqual = false;
                    }

                    if (score > bestScore) {
                        bestMove = move;
                        bestScore = score;
                    }
                }
            }
            return bestMove;
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

        if (boardData.numberOfFloodedPositions() < 20) {
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

        if (boardData.numberOfFloodedPositions() < 20) {
            return new CatchingState(ghost);
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
        return "SearchingState";
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

    private void updateAnotherGhostsStates(Game game) {
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

        updateAnotherGhostsStates(game);

        Constants.MOVE move = null;
        while (move == null) {
            move = state.Handle(this, ghost, game, boardData, ghostStates, timeDue);
        }

//        System.out.println(boardData.toString());

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
