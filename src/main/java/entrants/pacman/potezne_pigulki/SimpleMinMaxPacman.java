package entrants.pacman.potezne_pigulki;

import entrants.BoardData;
import entrants.SimpleMinMax;
import pacman.controllers.PacmanController;
import pacman.game.Constants.MOVE;
import pacman.game.Game;



public class SimpleMinMaxPacman extends PacmanController {
	private BoardData boardData = new BoardData(null, false);

	@Override
	public MOVE getMove(Game game, long timeDue) {
		boardData.update(game);
		int depth = 9;
		
        SimpleMinMax smm = new SimpleMinMax();
        MOVE myMinMaxMove = smm.bestMove(boardData, depth);
        return myMinMaxMove;
	}
}