// import examples.commGhosts.POCommGhosts;
import entrants.ghosts.potezne_pigulki.StateGhosts;
import entrants.pacman.potezne_pigulki.NotQuiteIntelligentPacMan;
import entrants.pacman.potezne_pigulki.SimpleMCTSPacman;
import entrants.pacman.potezne_pigulki.SimpleMinMaxPacman;
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
        PacmanController pacmans[] = {new POPacMan(), new NotQuiteIntelligentPacMan(), new SimpleMinMaxPacman(), new SimpleMCTSPacman()};
        MASController ghosts[] = {new POCommGhosts(), new StateGhosts()};
        int trials = 20;
        makeTests(trials, pacmans, ghosts);

    }

    public static void makeTests(int trials, PacmanController[] pacmans, MASController[] ghosts) {
        LinkedList<TestStat> stats = new LinkedList<>();

        for (PacmanController p: pacmans) {
            for (MASController g: ghosts) {
            	LinkedList<Integer> scores = new LinkedList<>();
            	LinkedList<Integer> levels = new LinkedList<>();
            	LinkedList<Integer> times = new LinkedList<>();
            	String label = "";
                for (int i = 0; i < trials; ++i) {
//                    stats.add(singleTest(p, g));
                	TestStat stat = singleTest(p, g);
                	System.out.println(stat);
                	if (stat.e == null) {
                		scores.add(stat.score);
                		levels.add(stat.level);
                		times.add(stat.time);
                		label = stat.label;
                	}
                }
                System.out.format("SUMMARY OF %s - games: %d, av. score: %.0f, av. level: %.1f, av. time: %.0f\n\n",
                		label,
                		scores.size(),
                		scores.stream().mapToInt(Integer::intValue).average().getAsDouble(),
                		levels.stream().mapToInt(Integer::intValue).average().getAsDouble(),
                		times.stream().mapToInt(Integer::intValue).average().getAsDouble());
            }
        }

//        // wypisz wyniki:
//        for (TestStat s: stats)
//            if (s.e == null)
//                System.out.println(s.toString());
//        // wypisz błędy:
//        for (TestStat s: stats)
//            if (s.e != null)
//                System.out.println(s.toString());
    }

    public static TestStat singleTest(PacmanController pacmanController, MASController ghostsController) {
        String label = pacmanController.getClass().getSimpleName() + " vs " + ghostsController.getClass().getSimpleName();

//        BasicMessenger messenger = new BasicMessenger(0, 1, 1);
//        Random rnd = new Random(0L);
//        MASController ghostControllerCopy = ghostsController.copy(true);

        try {
//            Game game = new Game(rnd.nextLong(), messenger);
//            while (!game.gameOver()) {
//                game.advanceGame(
//                        pacmanController.getMove(game.copy(Constants.GHOST.values().length + 1), System.currentTimeMillis() + 40L),
//                        ghostControllerCopy.getMove(game.copy(), System.currentTimeMillis() + 40L)
//                );
//            }
        	
             MyExecutor executor = new MyExecutor(true, true);
             Game game = executor.runGame(new NotQuiteIntelligentPacMan(), new StateGhosts(), false, 0);
        	
            return new TestStat(label, game.getScore(), game.getCurrentLevel(), game.getTotalTime());
        }
        catch (Exception e) {
            return new TestStat(label, e);
        }
    }
}