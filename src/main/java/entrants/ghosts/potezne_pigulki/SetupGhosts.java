package entrants.ghosts.potezne_pigulki;

import pacman.controllers.IndividualGhostController;
import pacman.controllers.MASController;
import pacman.game.Constants;
import pacman.game.Game;

import java.util.EnumMap;
import java.util.Arrays;
import java.util.List;


class GoLeftGhost extends IndividualGhostController {
    // duszek idzie lewo jak tylko może

    public GoLeftGhost(Constants.GHOST ghost) {
        super(ghost);
    }

    @Override
    public Constants.MOVE getMove(Game game, long timeDue) {
        return Constants.MOVE.LEFT;
    }
}


class GoAroundGhost extends IndividualGhostController {
    // duszek chodzi w koło trzymając się ściany po jego prawej

    private List<Constants.MOVE> moves = Arrays.asList(Constants.MOVE.LEFT, Constants.MOVE.DOWN, Constants.MOVE.RIGHT, Constants.MOVE.UP);

    public GoAroundGhost(Constants.GHOST ghost) {
        super(ghost);
    }

    @Override
    public Constants.MOVE getMove(Game game, long timeDue) {
        Constants.MOVE last_move = game.getGhostLastMoveMade(this.ghost);
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


public class SetupGhosts extends MASController {

    public SetupGhosts() {
        super(true, new EnumMap<Constants.GHOST, IndividualGhostController>(Constants.GHOST.class));
        controllers.put(Constants.GHOST.BLINKY, new GoLeftGhost(Constants.GHOST.BLINKY));
        controllers.put(Constants.GHOST.PINKY, new GoAroundGhost(Constants.GHOST.PINKY));
        controllers.put(Constants.GHOST.INKY, new GoToPillGhost(Constants.GHOST.INKY));
        controllers.put(Constants.GHOST.SUE, new LeftRightGhost(Constants.GHOST.SUE));
    }

}