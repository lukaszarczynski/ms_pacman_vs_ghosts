package entrants.pacman.potezne_pigulki;

import entrants.BoardData;
import entrants.SimpleMCTS;
import pacman.controllers.PacmanController;
import pacman.game.Game;
import pacman.game.Constants.MOVE;

public class SimpleMCTSPacman extends PacmanController {
	private BoardData boardData = new BoardData(null, false);

	@Override
	public MOVE getMove(Game game, long timeDue) {
		boardData.update(game);
		
		SimpleMCTS mcts = new SimpleMCTS();
        MOVE myMTCSMove = mcts.bestMove(boardData);
        return myMTCSMove;
	}
	
}