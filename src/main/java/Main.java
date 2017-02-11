// import examples.commGhosts.POCommGhosts;
import entrants.ghosts.potezne_pigulki.SetupGhosts;
import pacman.Executor;
import examples.poPacMan.POPacMan;
import examples.demo.DemoPacMan;


/**
 * Created by pwillic on 06/05/2016.
 */
public class Main {

    public static void main(String[] args) {

        Executor executor = new Executor(true, true);

        executor.runGame(new POPacMan(), new SetupGhosts(), true, 40);
    }
}
