package entrants.ghosts.potezne_pigulki;

import pacman.controllers.IndividualGhostController;
import pacman.controllers.MASController;
import pacman.game.Constants;
import pacman.game.Game;

import java.util.EnumMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

interface State {
    Constants.MOVE Handle(GhostContext context, Game game, long timeDue);
    Constants.MOVE getMove(Game game);
    State transitionFunction(Game game);
    State transitionFunction(Game game, Constants.GHOST anotherGhost);
}

class GoLeftState implements State {
    // duszek idzie lewo jak tylko może
    private Constants.GHOST ghost;
    private Integer startTime;
    private Integer maxDuration;

    GoLeftState(Constants.GHOST ghost, Game game)
    {
        this.ghost = ghost;
        startTime = game.getCurrentLevelTime();
        maxDuration = game.getGhostEdibleTime(ghost);
    }

    @Override
    public Constants.MOVE getMove(Game game) {
        Boolean requiresAction = game.doesGhostRequireAction(ghost);
        if (requiresAction != null && requiresAction)        //if ghost requires an action
        {
            return Constants.MOVE.LEFT;
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
            return new GoAroundState(ghost, game);
        }
        return this;
    }

    @Override
    public Constants.MOVE Handle(GhostContext context, Game game, long timeDue) {
        State stateAfterTransition = transitionFunction(game);
        if (stateAfterTransition != this) {
            context.state = stateAfterTransition;
            return null;
        }

        return getMove(game);
    }
}


class GoAroundState implements State {
    // duszek chodzi w koło trzymając się ściany po jego prawej
    private Constants.GHOST ghost;

    GoAroundState(Constants.GHOST ghost, Game game)
    {
        this.ghost = ghost;
    }

    private List<Constants.MOVE> moves = Arrays.asList(Constants.MOVE.LEFT, Constants.MOVE.DOWN, Constants.MOVE.RIGHT, Constants.MOVE.UP);

    @Override
    public Constants.MOVE getMove(Game game) {
        Boolean requiresAction = game.doesGhostRequireAction(ghost);
        if (requiresAction != null && requiresAction)        //if ghost requires an action
        {
            Constants.MOVE last_move = game.getGhostLastMoveMade(ghost);
            int last_move_idx = this.moves.indexOf(last_move);
            List<Constants.MOVE> allowed_moves = Arrays.asList(game.getPossibleMoves(
                    game.getGhostCurrentNodeIndex(ghost), last_move)
            );
            for (int i = 0; i < 4; ++i) {
                Constants.MOVE move = this.moves.get((last_move_idx + 3 + i) % 4);
                if (allowed_moves.contains(move))
                    return move;
            }
            return null;
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
            return new GoLeftState(ghost, game);
        }
        return this;
    }

    @Override
    public Constants.MOVE Handle(GhostContext context, Game game, long timeDue) {
        State stateAfterTransition = transitionFunction(game);
        if (stateAfterTransition != this) {
            context.state = stateAfterTransition;
            return null;
        }

        return getMove(game);
    }
}


class GoToPillGhost extends IndividualGhostController {
    // duszek idzie do superpillsa jak go zobaczy, ale jak do niego dojdzie idzie dalej, więc nie wiele to różni się
    // od losowych ruchów z przypadkowym przejściem przez superpillsa

    public GoToPillGhost(Constants.GHOST ghost) {
        super(ghost);
    }

    @Override
    public Constants.MOVE getMove(Game game, long timeDue) {
        int[] powerPills = game.getPowerPillIndices();
        for (int i = 0; i < powerPills.length; i++) {
            Boolean powerPillStillAvailable = game.isPowerPillStillAvailable(i);
            if (powerPillStillAvailable == null || powerPillStillAvailable == false)
                continue;
            // System.out.println(String.format("Go to superpill %d", powerPills[i]));
            return game.getNextMoveTowardsTarget(
                    game.getGhostCurrentNodeIndex(this.ghost),
                    powerPills[i],
                    game.getGhostLastMoveMade(this.ghost),
                    Constants.DM.PATH
            );
        }
        // System.out.println("NO PILL!!");
        return Constants.MOVE.NEUTRAL;
    }
}


class LeftRightGhost extends IndividualGhostController {
    // przykład tego, że duszek nie potrafi obrócić się o 180 stopni, tylko o 90.

    private int counter = 0;
    public LeftRightGhost(Constants.GHOST ghost) {
        super(ghost);
    }

    @Override
    public Constants.MOVE getMove(Game game, long timeDue) {
        Constants.MOVE last_move = game.getGhostLastMoveMade(this.ghost);
        List<Constants.MOVE> allowed_moves = Arrays.asList(game.getPossibleMoves(
            game.getGhostCurrentNodeIndex(ghost), last_move)
        );
        Constants.MOVE moves[] = {Constants.MOVE.LEFT, Constants.MOVE.RIGHT};
        return moves[(this.counter++) % 2];
    }
}


class GhostContext extends IndividualGhostController {
    State state;
    private HashMap<Constants.GHOST, State> ghostStates;

    GhostContext(Constants.GHOST ghost) {
        super(ghost);
        ghostStates = new HashMap<>();
    }

    @Override
    public Constants.MOVE getMove(Game game, long timeDue) {
        if (state == null) {
            state = new GoAroundState(ghost, game);
            for (Constants.GHOST anotherGhost : Constants.GHOST.values()) {
                ghostStates.put(anotherGhost, new GoAroundState(ghost, game));
            }
        }

        Constants.MOVE move = null;
        while (move == null) {
            move = state.Handle(this, game, timeDue);
        }

        for (Constants.GHOST anotherGhost : Constants.GHOST.values()) {
            State stateAfterTransition = ghostStates.get(anotherGhost).transitionFunction(game, anotherGhost);
            ghostStates.put(anotherGhost, stateAfterTransition);
        }

        return move;
    }
}


public class SetupGhosts extends MASController {

    public SetupGhosts() {
        super(true, new EnumMap<Constants.GHOST, IndividualGhostController>(Constants.GHOST.class));
        controllers.put(Constants.GHOST.BLINKY, new GhostContext(Constants.GHOST.BLINKY));
        controllers.put(Constants.GHOST.PINKY, new GhostContext(Constants.GHOST.PINKY));
        controllers.put(Constants.GHOST.INKY, new GhostContext(Constants.GHOST.INKY));
        controllers.put(Constants.GHOST.SUE, new GhostContext(Constants.GHOST.SUE));
    }

}