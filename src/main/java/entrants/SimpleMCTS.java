package entrants;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.info.GameInfo;
import pacman.game.internal.Ghost;
import pacman.game.internal.PacMan;
import pacman.game.Game;

public class SimpleMCTS {
	
	public MOVE bestMove(BoardData boardData) {
		Tree mtcs = new Tree(boardData.getClientGhost());
		return mtcs.MCTSSearch(boardData);
	}
}

class Tree {
	private GHOST player;
	private Random random = new Random();
	private long timeLimit = 35;
	private int playoutLimit = 50;
	private Game startGame = null;

	private BoardData startBoardData;
	private boolean pacmanFresh = true;
	private EnumMap<GHOST, Boolean> ghostFresh = new EnumMap<>(GHOST.class);
	
	public Tree(GHOST player) {
		this.player = player;
	}
	
	public MOVE MCTSSearch(BoardData boardData) {
		startGame = getPossibleCOGameFromPOGame(boardData);
		long startTime = System.currentTimeMillis();
		Node startNode = new Node(null, null, startGame);
		int counter = 0;
		while (System.currentTimeMillis() - startTime < timeLimit) {
			Node v = TreePolicy(startNode);			
			double delta = DefaultPolicy(v);
			Backup(v, delta);
			++counter;
		}
		return BestChild(startNode).moveFromParent;
	}
	
	private Node TreePolicy(Node v) {
		while (nonterminalState(v)) {	
			if (!v.full) {
				return Expand(v);
			}
			else {
				v = BestChild(v);
			}
		}
		return v;
	}
	
	private Node Expand(Node v) {
		MOVE[] actions = actions(v);
		for (MOVE move : actions) {
			if (!v.children.containsKey(move)) {
				Node child = new Node(v, move, transition(v, move));
				if (v.children.size() == actions.length) {
					v.full = true;
				}
				return child;
			}
		}
		v.full = true;
		return null;
	}
	
	private Node BestChild(Node v) {
		return BestChild(v, 1);
	}
	
	private Node BestChild(Node v, double c) {
		double bestUCT = Double.NEGATIVE_INFINITY;
		Node bestChild = null;
		for (MOVE move : MOVE.values()) {
			if (v.children.containsKey(move)) {
				Node child = v.children.get(move);
				double UCT = child.reward / child.visits + 
						c * Math.sqrt(2 * Math.log(v.visits) / child.visits);
				if (UCT > bestUCT) {
					bestUCT = UCT;
					bestChild = child;
				}
			}
		}
		return bestChild;
	}
	
	private double DefaultPolicy(Node v) {
		Game state = v.game;
		while (nonterminalState(state)) {
			MOVE[] actions = actions(state);
			state = transition(state, actions[random.nextInt(actions.length)]);
		}
		return value(state);
	}
	
	private void Backup(Node v, double delta) {
		while (v != null) {
			
			if (v.parent != null && 
					v.game.getPacmanLastMoveMade() == v.parent.game.getPacmanLastMoveMade().opposite()) {
				v.reward -= 1000000;
			}
			
			v.visits++;
			v.reward += delta;
			v = v.parent;
		}
	}
	
	
	
	private MOVE[] actions(Node v) {
		return actions(v.game);
	}
	
	private MOVE[] actions(Game game) {
		if (player == null) {
			return game.getPossibleMoves(game.getPacmanCurrentNodeIndex()/*,
					game.getPacmanLastMoveMade()*/);
		}
		else {
			return game.getPossibleMoves(game.getGhostCurrentNodeIndex(player),
					game.getGhostLastMoveMade(player));
		}
	}
	
	private Game transition(Node v, MOVE move) {
		return transition(v.game, move);
	}
	
	private Game transition(Game game, MOVE move) {
		Game oldGame = game;
		Game newGame = oldGame.copy();
//		newGame.updatePacMan(move);
		EnumMap<GHOST, MOVE> ghostMoves = new EnumMap<GHOST, MOVE>(GHOST.class);
		for (GHOST ghost : GHOST.values()) {
			MOVE[] possibleMoves = game.getPossibleMoves(game.getGhostCurrentNodeIndex(ghost),
					game.getGhostLastMoveMade(ghost));
			MOVE selectedMove;
			if (possibleMoves.length > 0) {
				selectedMove = possibleMoves[random.nextInt(possibleMoves.length)];
			}
			else {
				selectedMove = MOVE.NEUTRAL;
			}
			ghostMoves.put(ghost, selectedMove);
		}
//		newGame.updateGhosts(ghostMoves);
//		newGame.updateGame();
		newGame.advanceGame(move, ghostMoves);
		return newGame;
	}
	
	private boolean nonterminalState(Node v) {
		return nonterminalState(v.game);
	}	
	private boolean nonterminalState(Game game) {
		return !((game.getPacmanNumberOfLivesRemaining() < startGame.getPacmanNumberOfLivesRemaining()) ||
				(game.getTotalTime() - startGame.getTotalTime() > playoutLimit));
	}
	
	private double value(Game game) {
		if (game.getPacmanNumberOfLivesRemaining() < 
				startGame.getPacmanNumberOfLivesRemaining()) {
			return -1000000000;
		}
		else {
			return game.getScore();
		}
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

class Node {
	public Node parent = null;
	public double reward = 0;
	public int visits = 0;
	public EnumMap<MOVE, Node> children = new EnumMap<>(MOVE.class);
	public boolean full = false;
	public MOVE moveFromParent = MOVE.NEUTRAL;
	public int depth = 0;
	
	public Game game;
	
	public Node(Node parent, MOVE move, Game game) {
		this.parent = parent;
		this.moveFromParent = move;
		if (parent != null) {
			parent.children.put(move, this);
			depth = parent.depth + 1;
		}
		this.game = game;
	}
	
	public String toString() {
		String s = "";
		for (MOVE move : MOVE.values()) {
			if (children.containsKey(move)) {
				s += children.get(move).toString();
			}
		}
		return String.format("( %s %.0f/%d %s)", moveFromParent, reward, visits, s);
	}
}

