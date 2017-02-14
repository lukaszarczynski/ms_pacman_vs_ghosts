package entrants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import javax.swing.text.StyledEditorKit.BoldAction;

import pacman.game.Constants.DM;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.info.GameInfo;
import pacman.game.internal.Ghost;
import pacman.game.internal.PacMan;
import pacman.game.Game;

public class SimpleMinMax {
    private Random random = new Random();
	private GHOST player;
	private BoardData startBoardData;
	private boolean pacmanFresh = true;
	private EnumMap<GHOST, Boolean> ghostFresh = new EnumMap<>(GHOST.class);
	
	
	
	public SimpleMinMax() {
		for (GHOST ghost : GHOST.values()) {
			ghostFresh.put(ghost, true);
		}
	}
	
	class Result {
		public double value;
		public MOVE move;
		public Result(double value, MOVE move) {
			this.value = value;
			this.move = move;
		}
	}
	
	public MOVE bestMove(BoardData boardData, int depth) {
		Game coGame = getPossibleCOGameFromPOGame(boardData);
		MOVE selectedMove = bestResult(coGame, depth, player == null).move;
		return selectedMove;
	}
	
	/** ocena pozycji */
	private double getPositionValue(Game coGame) {
		double value = 0;
		if (coGame.getPacmanNumberOfLivesRemaining() < 
				startBoardData.getGame().getPacmanNumberOfLivesRemaining()) {
			value = -10000000;
		}
		else {
			value = 10 * coGame.getScore();
			

        	if (player == null) {
        		// nagroda za zbliżanie się do jakiejś pigułki
        		int newIndex = coGame.getPacmanCurrentNodeIndex();
        		int closestPillIndex = coGame.getClosestNodeIndexFromNodeIndex(
        				newIndex, coGame.getActivePillsIndices(), DM.PATH);
        		int distanceToPill = coGame.getShortestPathDistance(newIndex, closestPillIndex);
        		value -= Math.sqrt(distanceToPill);
        		
        		// ucieka od duchów, lecz idzie ku nim, gdy są jadalne
        		for (GHOST ghost : GHOST.values()) {
        			if (coGame.getGhostCurrentNodeIndex(ghost) != -1) {//        				
        				int distanceToGhost = coGame.getShortestPathDistance(
        												newIndex, 
        												coGame.getGhostCurrentNodeIndex(ghost));
        				if (coGame.getGhostLairTime(ghost) > 0) {
        					value += 100;
        				}
//        				if (coGame.isGhostEdible(ghost)) {
////        					value += 1000. / distanceToGhost;
//
//        				}
//        				else {
////            				value -= 100000. / distanceToGhost;
//        				}
        			}
        		}
        	}
        	
        	value += random.nextFloat();
		}
		return value;
	}
	
	
	private Result bestResult(Game coGame, int depth, boolean pacmanTurn) {
		if (coGame.gameOver() || depth == 0) {
			return new Result(getPositionValue(coGame), MOVE.NEUTRAL);
		}
		else {
			if (pacmanTurn) {			// jeśli ruch pacmana
				if (pacmanFresh) {
					Result bestCurrentResult = new Result(-Integer.MAX_VALUE, MOVE.NEUTRAL);
			        for (MOVE pacmanMove : coGame.getPossibleMoves(coGame.getPacmanCurrentNodeIndex())) {
			        	
			            Game forwardCopy = coGame.copy();
			            forwardCopy.updatePacMan(pacmanMove);
			            forwardCopy.updateGame();
			            
			            Result currentResult = bestResult(forwardCopy, depth - 1, !pacmanTurn);
			            
			            
			            // kara dla pacmana za powtarzanie ruchów
			            int newIndex = forwardCopy.getPacmanCurrentNodeIndex();
		            	List<Integer> tabuList = startBoardData.getClientIndicesHistory();
		            	int tabuLimit = 100;
		        		for (int i = 0; i < tabuList.size() && i < tabuLimit; ++i) {
		        			int oldIndex = tabuList.get(tabuList.size() - 1 - i);
		        			if (newIndex == oldIndex) {
		        				currentResult.value -= depth / (i + 1.); 
		        			}
		        		}
		        		
			            if (currentResult.value > bestCurrentResult.value) {
			            	bestCurrentResult = new Result(currentResult.value, pacmanMove);
			            }
			        }
			        return bestCurrentResult;
				}
				else {
		            coGame.updateGame();
					return bestResult(coGame, depth - 1, !pacmanTurn);
				}
			}
			else {						// jeśli ruch duszków
				Result bestCurrentResult = new Result(Integer.MAX_VALUE, MOVE.NEUTRAL);
				List<EnumMap<GHOST, MOVE>> combinations = getGhostMovesCombinations(coGame);
				for (EnumMap<GHOST, MOVE> combination : combinations) {
					Game forwardCopy = coGame.copy();
					forwardCopy.updateGhosts(combination);
		            forwardCopy.updateGame();					            
		            Result currentResult = bestResult(forwardCopy, depth - 1, !pacmanTurn);
		            
		            if (currentResult.value < bestCurrentResult.value) {
		            	bestCurrentResult = new Result(currentResult.value, player != null ? combination.get(player) : null);
		            }
				}
				
		        return bestCurrentResult;
			}
		}
	}
	
	private List<EnumMap<GHOST, MOVE>> getGhostMovesCombinations(Game coGame) {
		EnumMap<GHOST, MOVE[]> ghostsPossibleMoves = new EnumMap<GHOST, MOVE[]>(GHOST.class);
		for (GHOST ghost : GHOST.values()) {
			MOVE[] possibleMoves;
			if (ghostFresh.get(ghost)) {
				possibleMoves = coGame.getPossibleMoves(coGame.getGhostCurrentNodeIndex(ghost), coGame.getGhostLastMoveMade(ghost));
				if (possibleMoves.length == 0) {
					possibleMoves = new MOVE[] {MOVE.NEUTRAL};
				}
			}
			else {
				possibleMoves = new MOVE[] {MOVE.NEUTRAL};
			}
			ghostsPossibleMoves.put(ghost, possibleMoves);
		}
		
		List<EnumMap<GHOST, MOVE>> ghostsMovesCombinations = new LinkedList<EnumMap<GHOST, MOVE>>();
		for (MOVE blinkyMove : ghostsPossibleMoves.get(GHOST.BLINKY)) {
			for (MOVE inkyMove : ghostsPossibleMoves.get(GHOST.INKY)) {
				for (MOVE pinkyMove : ghostsPossibleMoves.get(GHOST.PINKY)) {
					for (MOVE sueMove : ghostsPossibleMoves.get(GHOST.SUE)) {
						EnumMap<GHOST, MOVE> ghostsMovesCombination = new EnumMap<GHOST, MOVE>(GHOST.class);
						ghostsMovesCombination.put(GHOST.BLINKY, blinkyMove);
						ghostsMovesCombination.put(GHOST.INKY, inkyMove);
						ghostsMovesCombination.put(GHOST.PINKY, pinkyMove);
						ghostsMovesCombination.put(GHOST.SUE, sueMove);
						ghostsMovesCombinations.add(ghostsMovesCombination);
					}
				}
			}
		}
		
		return ghostsMovesCombinations;
	}

	
	private Game getPossibleCOGameFromPOGame (BoardData boardData) {
		player = boardData.getClientGhost();
		startBoardData = boardData;
		Game game = boardData.getGame();
		int dataFreshnessTreshold = 10;	// jak stare informacje traktować jako aktualne
				
		GameInfo info = game.getBlankGameInfo();

        for (int activePill : boardData.getRemainingPillIndices()) {
            info.setPillAtIndex(game.getPillIndex(activePill), true);
        }

        for (int activePowerPill : boardData.getRemainingPowerPillsIndices()) {
            info.setPowerPillAtIndex(game.getPowerPillIndex(activePowerPill), true);
        }

        int pacmanIndex = game.getPacmanCurrentNodeIndex();
        if (pacmanIndex != -1) {
            info.setPacman(new PacMan(
            		pacmanIndex,
                    game.getPacmanLastMoveMade(),
                    game.getPacmanNumberOfLivesRemaining(),
                    false
            ));
        }
        else {
        	int pacmanApproxIndex;
        	if (boardData.expired(boardData.getPacmanIndex().time, dataFreshnessTreshold)) {
        		pacmanApproxIndex = game.getGhostInitialNodeIndex();
        		pacmanFresh = false;
        	}
        	else {
        		pacmanApproxIndex = boardData.getPacmanIndex().value;
        	}
            info.setPacman(new PacMan(
            		pacmanApproxIndex,
                    MOVE.NEUTRAL,
                    game.getPacmanNumberOfLivesRemaining(),
                    false
            ));
        }
       
        for(GHOST ghost : GHOST.values()){
            int index = game.getGhostCurrentNodeIndex(ghost);
            if (index != -1) {
                info.setGhostIndex(ghost,
                        new Ghost(
                                ghost,
                                index,
                                game.getGhostEdibleTime(ghost),
                                game.getGhostLairTime(ghost),
                                game.getGhostLastMoveMade(ghost)
                        ));
            }
            else {        	
            	int ghostApproxIndex;
	        	if (boardData.expired(boardData.getGhostIndex(ghost).time, dataFreshnessTreshold)) {
	        		ghostApproxIndex = game.getGhostInitialNodeIndex();
	        		ghostFresh.put(ghost, false);
	        	}
	        	else {
	        		ghostApproxIndex = boardData.getGhostIndex(ghost).value;
	        	}
                info.setGhostIndex(ghost,
                        new Ghost(
                                ghost,
                                ghostApproxIndex,
                                -1,
                                -1,
                                MOVE.NEUTRAL
                        ));
            }
        }
        
        return game.getGameFromInfo(info);
	}

}