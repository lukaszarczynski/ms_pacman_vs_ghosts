// import examples.commGhosts.POCommGhosts;
import entrants.ghosts.potezne_pigulki.StateGhosts;
import entrants.pacman.potezne_pigulki.NotQuiteIntelligentPacMan;
import examples.commGhosts.POCommGhosts;
import examples.poPacMan.POPacMan;
import pacman.Executor;


/**
 * Created by pwillic on 06/05/2016.
 */
public class Main {

    public static void main(String[] args) {

        Executor executor = new Executor(true, true);

        executor.runGame(new NotQuiteIntelligentPacMan(), new StateGhosts(), true, 40);
    }
}