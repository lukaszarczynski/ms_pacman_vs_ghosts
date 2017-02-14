// import examples.commGhosts.POCommGhosts;
import entrants.ghosts.potezne_pigulki.StateGhosts;
import entrants.pacman.potezne_pigulki.NotQuiteIntelligentPacMan;
import examples.poPacMan.POPacMan;
import examples.commGhosts.POCommGhosts;
import pacman.Executor;
import pacman.controllers.MASController;
import pacman.controllers.PacmanController;
import pacman.game.Constants;
import pacman.game.Game;
import pacman.game.comms.BasicMessenger;

import java.util.Random;
import java.util.LinkedList;

import java.io.PrintWriter;
import java.io.StringWriter;


/**
 * Created by pwillic on 06/05/2016.
 */

class TestStat {
    public String label;
    public int score;
    public int level;
    public int time;
    public Exception e = null;

    public TestStat (String label, int score, int level, int time) {
        this.label = label;
        this.score = score;
        this.level = level;
        this.time = time;
    }

    public TestStat (String label, Exception e) {
        this.label = label;
        this.e = e;
    }

    public String toString() {
        if (this.e != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            return String.format("%s - error:\n  %s", this.label, sw.toString());
        }
        return String.format("%s - score: %d, level: %d, time: %d", this.label, this.score, this.level, this.time);
    }
}

public class Main {

    public static void main(String[] args) {
        makeTests(20);

        // Executor executor = new Executor(true, true);
        // executor.runGame(new NotQuiteIntelligentPacMan(), new StateGhosts(), true, 40);
    }

    public static void makeTests(int trials) {
        // NOTE: tutaj wszystko to, co chcemy testować:
        PacmanController pacmans[] = {new POPacMan(), new NotQuiteIntelligentPacMan()};
        MASController ghosts[] = {new POCommGhosts(), new StateGhosts()};
        ////////////////

        LinkedList<TestStat> stats = new LinkedList<>();

        for (PacmanController p: pacmans) {
            for (MASController g: ghosts) {
                for (int i = 0; i < trials; ++i) {
                    stats.add(singleTest(p, g));
                }
            }
        }

        // wypisz wyniki:
        for (TestStat s: stats)
            if (s.e == null)
                System.out.println(s.toString());
        // wypisz błędy:
        for (TestStat s: stats)
            if (s.e != null)
                System.out.println(s.toString());
    }

    public static TestStat singleTest(PacmanController pacmanController, MASController ghostsController) {
        String label = pacmanController.getClass().getSimpleName() + " vs " + ghostsController.getClass().getSimpleName();

        BasicMessenger messenger = new BasicMessenger(0, 1, 1);
        Random rnd = new Random(0L);
        MASController ghostControllerCopy = ghostsController.copy(true);

        try {
            Game game = new Game(rnd.nextLong(), messenger);
            while (!game.gameOver()) {
                game.advanceGame(
                        pacmanController.getMove(game.copy(Constants.GHOST.values().length + 1), System.currentTimeMillis() + 40L),
                        ghostControllerCopy.getMove(game.copy(), System.currentTimeMillis() + 40L)
                );
            }
            return new TestStat(label, game.getScore(), game.getCurrentLevel(), game.getTotalTime());
        }
        catch (Exception e) {
            return new TestStat(label, e);
        }
    }
}
