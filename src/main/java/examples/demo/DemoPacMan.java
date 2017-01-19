package examples.demo;

import pacman.Executor;
import pacman.controllers.MASController;
import pacman.controllers.PacmanController;
import static pacman.game.Constants.*;

import pacman.controllers.examples.po.POCommGhosts;
import pacman.game.Game;
import pacman.game.info.GameInfo;
import pacman.game.internal.Ghost;
import pacman.game.internal.PacMan;

import java.util.Arrays;
import java.util.EnumMap;

/**
 * Created by piers on 04/10/16.
 */
public class DemoPacMan extends PacmanController {
    @Override
    public MOVE getMove(Game game, long timeDue) {
        Game coGame;
        if(game.isGamePo()){
            GameInfo info = game.getBlankGameInfo();

            int[] activePills = game.getActivePillsIndices();
            int[] activePowerPills = game.getActivePowerPillsIndices();

            for (int activePill : activePills) {
                info.setPillAtIndex(game.getPillIndex(activePill), true);
            }

            for (int activePowerPill : activePowerPills) {
                info.setPowerPillAtIndex(game.getPowerPillIndex(activePowerPill), true);
            }

            info.setPacman(new PacMan(
                    game.getPacmanCurrentNodeIndex(),
                    game.getPacmanLastMoveMade(),
                    game.getPacmanNumberOfLivesRemaining(),
                    false
            ));

            for(GHOST ghost : GHOST.values()){
                int index = game.getGhostCurrentNodeIndex(ghost);
                if(index != -1){
                    info.setGhostIndex(ghost,
                            new Ghost(
                                    ghost,
                                    index,
                                    game.getGhostEdibleTime(ghost),
                                    game.getGhostLairTime(ghost),
                                    game.getGhostLastMoveMade(ghost)
                            ));
                }else{
                    info.setGhostIndex(ghost,
                            new Ghost(
                                    ghost,
                                    game.getCurrentMaze().lairNodeIndex,
                                    -1,
                                    -1,
                                    MOVE.NEUTRAL
                            ));
                }
            }

//            info.setGhostIndex(GHOST.INKY, new Ghost(GHOST.INKY, 25, 100, 0, MOVE.DOWN));
//            info.setGhostIndex(GHOST.BLINKY, new Ghost(GHOST.BLINKY, 46, 0, 0, MOVE.UP));
//            info.setGhostIndex(GHOST.PINKY, new Ghost(GHOST.PINKY, 256, 100, 0, MOVE.RIGHT));
//            info.setGhostIndex(GHOST.SUE, new Ghost(GHOST.SUE, 479, 100, 0, MOVE.LEFT));

            coGame = game.getGameFromInfo(info);

        }else{
            coGame = game.copy();
        }

        // Make some ghosts
        MASController ghosts = new POCommGhosts(50);
        // Ask what they would do
        EnumMap<GHOST, MOVE> ghostMoves = ghosts.getMove(coGame.copy(), 40);

        // Get the best one step lookahead move
        MOVE bestMove = null;
        int bestScore = -Integer.MAX_VALUE;
        for(MOVE move : MOVE.values()){
            Game forwardCopy = coGame.copy();
            forwardCopy.advanceGame(move, ghostMoves);
            int score = forwardCopy.getScore();
            if(score > bestScore){
                bestMove = move;
                bestScore = score;
            }
        }

        System.out.println("Best MOVE: " + bestMove + " With Score: " + bestScore);
        return bestMove;
    }

    public static void main(String[] args) {
        Executor co = new Executor(false, false, false);
        Executor po = new Executor(true, true, true);

        co.setDaemon(true);
        po.setDaemon(true);

        co.runGame(new DemoPacMan(), new POCommGhosts(50), true, 40);
        po.runGame(new DemoPacMan(), new POCommGhosts(50), true, 40);
    }


}
