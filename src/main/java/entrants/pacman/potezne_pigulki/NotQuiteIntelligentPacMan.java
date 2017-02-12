package entrants.pacman.potezne_pigulki;

import pacman.controllers.PacmanController;
import pacman.game.Constants.DM;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import entrants.BoardData;

public class NotQuiteIntelligentPacMan extends PacmanController {
    private Random random = new Random();
    private BoardData boardData = new BoardData(null, false);
    private List<Integer> tabuList = new ArrayList<Integer>();
    private int tabuLimit = 100;

    @Override
    public MOVE getMove(Game game, long timeDue) {
    	if (game.getCurrentLevelTime() == 0) {
    		tabuList = new ArrayList<Integer>();
    	}
    	
    	boardData.update(game);
    	int currentPacmanIndex = game.getPacmanCurrentNodeIndex();
    	tabuList.add(currentPacmanIndex);
    	
        MOVE bestMove = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
    	for (MOVE move : game.getPossibleMoves(currentPacmanIndex)) {
    		double score = 0;

    		int newIndex = game.getNeighbour(currentPacmanIndex, move);
    		
    		// nagroda za zjedzenie pigułki
    		if (game.getPillIndex(newIndex) != -1 && 
    				game.isPillStillAvailable(game.getPillIndex(newIndex))) {
    			score += 10;
    		}
    		// nagroda za zjedzenie superpigułki
    		else if (game.getPowerPillIndex(newIndex) != -1 && 
    				game.isPowerPillStillAvailable(game.getPowerPillIndex(newIndex))) {
    			score += 30;
    		}
    		
    		// nagroda za zbliżanie się do jakiejś pigułki
    		int closestPillIndex = game.getClosestNodeIndexFromNodeIndex(newIndex, allPillsArray(), DM.PATH);
    		int distanceToPill = game.getShortestPathDistance(newIndex, closestPillIndex);
    		score -= distanceToPill;
    		
    		// ucieka od duchów, lecz idzie ku nim, gdy są jadalne
    		for (GHOST ghost : GHOST.values()) {
    			if (game.getGhostCurrentNodeIndex(ghost) != -1) {
    				int distanceToGhost = game.getShortestPathDistance(
    												newIndex, 
    												game.getGhostCurrentNodeIndex(ghost));
    				if (game.isGhostEdible(ghost)) {
    					score += 1000. / distanceToGhost;

    				}
    				else {
        				score -= 100000. / distanceToGhost;
    				}
    			}
    		}
    		
    		// kara za łażenie po odwiedzonych niedawno polach
    		for (int i = 0; i < tabuList.size() && i < tabuLimit; ++i) {
    			int oldIndex = tabuList.get(tabuList.size() - 1 - i);
    			if (newIndex == oldIndex) {
    				score -= 5. / (i + 1); 
    			}
    		}
    		
    		// losowy szum
    		score += (random.nextDouble() / 10);
    		
    		if (score > bestScore) {
    			bestScore = score;
    			bestMove = move;
    		}
    	}
    	
    	return bestMove;
    }
    
    
    private int[] allPillsArray() {
    	int[] allPills = new int[boardData.getRemainingPillIndices().size() + boardData.getRemainingPowerPillsIndices().size()];
    	int i = 0;
    	for (int pillIndex : boardData.getRemainingPillIndices()) {
    		allPills[i] = pillIndex;
    		++i;
    	}
    	for (int powerPillIndex : boardData.getRemainingPowerPillsIndices()) {
    		allPills[i] = powerPillIndex;
    		++i;
    	}
    	return allPills;
    }
}