package examples.poPacMan;

import pacman.controllers.PacmanController;
import pacman.game.Game;

import java.util.ArrayList;
import java.util.Random;

import static pacman.game.Constants.*;

/**
 * Created by Piers on 15/02/2016.
 */
public class POPacMan extends PacmanController {
    private static final int MIN_DISTANCE = 20;
    private Random random = new Random();

    @Override
    public MOVE getMove(Game game, long timeDue) {

        // Should always be possible as we are PacMan
        int current = game.getPacmanCurrentNodeIndex();

        // Strategy 1: Adjusted for PO
        for (GHOST ghost : GHOST.values()) {
            // If can't see these will be -1 so all fine there
            if (game.getGhostEdibleTime(ghost) == 0 && game.getGhostLairTime(ghost) == 0) {
                int ghostLocation = game.getGhostCurrentNodeIndex(ghost);
                if (ghostLocation != -1) {
                    if (game.getShortestPathDistance(current, ghostLocation) < MIN_DISTANCE) {
                        // System.out.println("MOVE (away from ghost): " + game.getNextMoveAwayFromTarget(current, ghostLocation, DM.PATH));
                        return game.getNextMoveAwayFromTarget(current, ghostLocation, DM.PATH);
                    }
                }
            }
        }

        /// Strategy 2: Find nearest edible ghost and go after them
        int minDistance = Integer.MAX_VALUE;
        GHOST minGhost = null;
        for (GHOST ghost : GHOST.values()) {
            // If it is > 0 then it is visible so no more PO checks
            if (game.getGhostEdibleTime(ghost) > 0) {
                int distance = game.getShortestPathDistance(current, game.getGhostCurrentNodeIndex(ghost));

                if (distance < minDistance) {
                    minDistance = distance;
                    minGhost = ghost;
                }
            }
        }

        if (minGhost != null) {
            // System.out.println("MOVE (edible ghost): " + game.getNextMoveTowardsTarget(current, game.getGhostCurrentNodeIndex(minGhost), DM.PATH));
            return game.getNextMoveTowardsTarget(current, game.getGhostCurrentNodeIndex(minGhost), DM.PATH);
        }

        // Strategy 3: Go after the pills and power pills that we can see
        int[] pills = game.getPillIndices();
        int[] powerPills = game.getPowerPillIndices();

        ArrayList<Integer> targets = new ArrayList<Integer>();

        for (int i = 0; i < pills.length; i++) {
            //check which pills are available
            try
            {
                Boolean pillStillAvailable = game.isPillStillAvailable(i);
                if (pillStillAvailable == null) continue;
            } catch(Exception e)
            {
                System.out.println("Exception: (Pill) " + e);
                continue;
            }
            if (game.isPillStillAvailable(i)) {
                targets.add(pills[i]);
            }
        }

        for (int i = 0; i < powerPills.length; i++) {            //check with power pills are available
            Boolean pillStillAvailable = game.isPillStillAvailable(i);
            if (pillStillAvailable == null) continue;
            try {
                if (game.isPowerPillStillAvailable(pills[i])) {
                    targets.add(powerPills[i]);
                }
            } catch (Exception e) {
                System.out.println("Exception: (Powerpill) " + e);
                continue;
            }
        }

        if (!targets.isEmpty()) {
            int[] targetsArray = new int[targets.size()];        //convert from ArrayList to array

            for (int i = 0; i < targetsArray.length; i++) {
                targetsArray[i] = targets.get(i);
            }
            //return the next direction once the closest target has been identified
            // System.out.println("MOVE (closest target): " + game.getNextMoveTowardsTarget(current, game.getClosestNodeIndexFromNodeIndex(current, targetsArray, DM.PATH), DM.PATH));
            return game.getNextMoveTowardsTarget(current, game.getClosestNodeIndexFromNodeIndex(current, targetsArray, DM.PATH), DM.PATH);
        }


        // Strategy 4: New PO strategy as now S3 can fail if nothing you can see
        // Going to pick a random action here
        MOVE[] moves = game.getPossibleMoves(current, game.getPacmanLastMoveMade());
        if (moves.length > 0) {
            MOVE selectedMove = moves[random.nextInt(moves.length)];
            // System.out.println("MOVE (random): " + selectedMove);
            return selectedMove;
        }

        // Must be possible to turn around
        // System.out.println("MOVE (opposite): " + game.getPacmanLastMoveMade().opposite());
        return game.getPacmanLastMoveMade().opposite();
    }
}
