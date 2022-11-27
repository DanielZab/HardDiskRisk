import at.ac.tuwien.ifs.sge.agent.AbstractGameAgent;
import at.ac.tuwien.ifs.sge.agent.GameAgent;
import at.ac.tuwien.ifs.sge.engine.Logger;
import at.ac.tuwien.ifs.sge.game.Game;
import at.ac.tuwien.ifs.sge.util.Util;
import at.ac.tuwien.ifs.sge.util.tree.DoubleLinkedTree;
import at.ac.tuwien.ifs.sge.util.tree.Tree;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class HardDiskRisk<G extends Game<A, ?>, A> extends AbstractGameAgent<G, A> implements GameAgent<G, A> {
    private static final int MAX_PRINT_THRESHOLD = 97;

    private static int INSTANCE_NR_COUNTER = 1;

    private final int instanceNr;

    private final double exploitationConstant;

    private Comparator<Tree<McGameNode<A>>> gameMcTreeUCTComparator;

    private Comparator<Tree<McGameNode<A>>> gameMcTreeSelectionComparator;

    private Comparator<Tree<McGameNode<A>>> gameMcTreePlayComparator;

    private Comparator<McGameNode<A>> gameMcNodePlayComparator;

    private Comparator<Tree<McGameNode<A>>> gameMcTreeWinComparator;

    private Comparator<McGameNode<A>> gameMcNodeWinComparator;

    private Comparator<Tree<McGameNode<A>>> gameMcTreeMoveComparator;

    private Comparator<McGameNode<A>> gameMcNodeMoveComparator;

    private Comparator<McGameNode<A>> gameMcNodeGameComparator;

    private Comparator<Tree<McGameNode<A>>> gameMcTreeGameComparator;

    private Tree<McGameNode<A>> mcTree;

    public HardDiskRisk() {
        this((Logger)null);
    }

    public HardDiskRisk(Logger log) {
        this(Math.sqrt(2.0D), log);
    }

    public HardDiskRisk(double exploitationConstant, Logger log) {
        super(log);
        this.exploitationConstant = exploitationConstant;
        this.mcTree = (Tree<McGameNode<A>>)new DoubleLinkedTree();
        this.instanceNr = INSTANCE_NR_COUNTER++;
    }

    public void setUp(int numberOfPlayers, int playerId) {
        super.setUp(numberOfPlayers, playerId);
        this.mcTree.clear();
        this.mcTree.setNode(new McGameNode());
        this.gameMcTreeUCTComparator = Comparator.comparingDouble(t -> upperConfidenceBound(t, this.exploitationConstant));
        this.gameMcNodePlayComparator = Comparator.comparingInt(McGameNode::getPlays);
        this.gameMcTreePlayComparator = ((o1, o2) -> this.gameMcNodePlayComparator.compare((McGameNode<A>)o1.getNode(), (McGameNode<A>)o2.getNode()));
        this.gameMcNodeWinComparator = Comparator.comparingInt(McGameNode::getWins);
        this.gameMcTreeWinComparator = ((o1, o2) -> this.gameMcNodeWinComparator.compare((McGameNode<A>)o1.getNode(), (McGameNode<A>)o2.getNode()));
        this.gameMcNodeGameComparator = ((o1, o2) -> this.gameComparator.compare(o1.getGame(), o2.getGame()));
        this.gameMcTreeGameComparator = ((o1, o2) -> this.gameMcNodeGameComparator.compare((McGameNode<A>)o1.getNode(), (McGameNode<A>)o2.getNode()));
        this.gameMcTreeSelectionComparator = this.gameMcTreeUCTComparator.thenComparing(this.gameMcTreeGameComparator);
        this.gameMcNodeMoveComparator = this.gameMcNodePlayComparator.thenComparing(this.gameMcNodeWinComparator).thenComparing(this.gameMcNodeGameComparator);
        this.gameMcTreeMoveComparator = ((o1, o2) -> this.gameMcNodeMoveComparator.compare((McGameNode<A>)o1.getNode(), (McGameNode<A>)o2.getNode()));
    }

    public A computeNextAction(G game, long computationTime, TimeUnit timeUnit) {
        setTimers(computationTime, timeUnit);
        this.log.inf_("HardDiskRisk playing");
        this.log.tra_("Searching for root of tree");
        boolean foundRoot = Util.findRoot(this.mcTree, (Game)game);
        if (foundRoot) {
            this.log._trace(", done.");
        } else {
            this.log._trace(", failed.");
        }
        this.log.tra_("Check if best move will eventually end game: ");
        if (sortPromisingCandidates(this.mcTree, this.gameMcNodeMoveComparator.reversed())) {
            this.log._trace("Yes");
            return (A)((McGameNode<A>)((Tree)Collections.<Tree>max(this.mcTree.getChildren(), (Comparator)this.gameMcTreeMoveComparator)).getNode()).getGame()
                    .getPreviousAction();
        }
        this.log._trace("No");
        int looped = 0;
        this.log.debf_("MCTS with %d simulations at confidence %.1f%%", new Object[] { Integer.valueOf(((McGameNode)this.mcTree.getNode()).getPlays()),
                Double.valueOf(Util.percentage(((McGameNode)this.mcTree.getNode()).getWins(), ((McGameNode)this.mcTree.getNode()).getPlays())) });
        int printThreshold = 1;
        while (!shouldStopComputation()) {
            if (looped++ % printThreshold == 0) {
                this.log._deb_("\r");
                this.log.debf_("MCTS with %d simulations at confidence %.1f%%", new Object[] { Integer.valueOf(((McGameNode)this.mcTree.getNode()).getPlays()),
                        Double.valueOf(Util.percentage(((McGameNode)this.mcTree.getNode()).getWins(), ((McGameNode)this.mcTree.getNode()).getPlays())) });
            }
            Tree<McGameNode<A>> tree = this.mcTree;
            tree = mcSelection(tree);
            mcExpansion(tree);
            boolean won = mcSimulation(tree, 128, 2);
            mcBackPropagation(tree, won);
            if (printThreshold < 97)
                printThreshold = Math.max(1, Math.min(97,
                        Math.round(((McGameNode)this.mcTree.getNode()).getPlays() * 11.111111F)));
        }
        long elapsedTime = Math.max(1L, System.nanoTime() - this.START_TIME);
        this.log._deb_("\r");
        this.log.debf_("MCTS with %d simulations at confidence %.1f%%", new Object[] { Integer.valueOf(((McGameNode)this.mcTree.getNode()).getPlays()),
                Double.valueOf(Util.percentage(((McGameNode)this.mcTree.getNode()).getWins(), ((McGameNode)this.mcTree.getNode()).getPlays())) });
        this.log._debugf(", done in %s with %s/simulation.", new Object[] { Util.convertUnitToReadableString(elapsedTime, TimeUnit.NANOSECONDS, timeUnit),

                Util.convertUnitToReadableString(elapsedTime / Math.max(1, ((McGameNode)this.mcTree.getNode()).getPlays()), TimeUnit.NANOSECONDS, TimeUnit.NANOSECONDS) });
        if (this.mcTree.isLeaf()) {
            this.log._debug(". Could not find a move, choosing the next best greedy option.");
            return Collections.max(game.getPossibleActions(), (o1, o2) -> this.gameComparator.compare(game.doAction(o1), game.doAction(o2)));
        }
        return (A)((McGameNode<A>)((Tree)Collections.<Tree>max(this.mcTree.getChildren(), (Comparator)this.gameMcTreeMoveComparator)).getNode()).getGame()
                .getPreviousAction();
    }

    private boolean sortPromisingCandidates(Tree<McGameNode<A>> tree, Comparator<McGameNode<A>> comparator) {
        boolean isDetermined = true;
        while (!tree.isLeaf() && isDetermined) {
            isDetermined = tree.getChildren().stream().allMatch(c -> (((McGameNode<A>)c.getNode()).getGame().getCurrentPlayer() >= 0));
            if (((McGameNode<A>)tree.getNode()).getGame().getCurrentPlayer() == this.playerId) {
                tree.sort(comparator);
            } else {
                tree.sort(comparator.reversed());
            }
            tree = tree.getChild(0);
        }
        return (isDetermined && ((McGameNode<A>)tree.getNode()).getGame().isGameOver());
    }

    private Tree<McGameNode<A>> mcSelection(Tree<McGameNode<A>> tree) {
        int depth = 0;
        while (!tree.isLeaf() && (depth++ % 31 != 0 || !shouldStopComputation())) {
            List<Tree<McGameNode<A>>> children = new ArrayList<>(tree.getChildren());
            if (((McGameNode<A>)tree.getNode()).getGame().getCurrentPlayer() < 0) {
                A action = (A)((McGameNode<A>)tree.getNode()).getGame().determineNextAction();
                for (Tree<McGameNode<A>> child : children) {
                    if (((McGameNode<A>)child.getNode()).getGame().getPreviousAction().equals(action))
                        tree = child;
                }
                continue;
            }
            tree = Collections.<Tree<McGameNode<A>>>max(children, this.gameMcTreeSelectionComparator);
        }
        return tree;
    }

    private void mcExpansion(Tree<McGameNode<A>> tree) {
        if (tree.isLeaf()) {
            Game<A, ?> game = ((McGameNode<A>)tree.getNode()).getGame();
            Set<A> possibleActions = game.getPossibleActions();
            for (A possibleAction : possibleActions)
                tree.add(new McGameNode<>(game, possibleAction));
        }
    }

    private boolean mcSimulation(Tree<McGameNode<A>> tree, int simulationsAtLeast, int proportion) {
        int simulationsDone = ((McGameNode)tree.getNode()).getPlays();
        if (simulationsDone < simulationsAtLeast && shouldStopComputation(proportion)) {
            int simulationsLeft = simulationsAtLeast - simulationsDone;
            return mcSimulation(tree, nanosLeft() / simulationsLeft);
        }
        if (simulationsDone == 0)
            return mcSimulation(tree, this.TIMEOUT / 2L - nanosElapsed());
        return mcSimulation(tree);
    }

    private boolean mcSimulation(Tree<McGameNode<A>> tree) {
        Game<A, ?> game = ((McGameNode<A>)tree.getNode()).getGame();
        int depth = 0;
        while (!game.isGameOver() && (depth++ % 31 != 0 || !shouldStopComputation())) {
            if (game.getCurrentPlayer() < 0) {
                game = game.doAction();
                continue;
            }
            game = game.doAction(Util.selectRandom(game.getPossibleActions(), this.random));
        }
        return mcHasWon(game);
    }

    private boolean mcSimulation(Tree<McGameNode<A>> tree, long timeout) {
        long startTime = System.nanoTime();
        Game<A, ?> game = ((McGameNode<A>)tree.getNode()).getGame();
        int depth = 0;
        while (!game.isGameOver() && System.nanoTime() - startTime <= timeout && (depth++ % 31 != 0 ||
                !shouldStopComputation())) {
            if (game.getCurrentPlayer() < 0) {
                game = game.doAction();
                continue;
            }
            game = game.doAction(Util.selectRandom(game.getPossibleActions(), this.random));
        }
        return mcHasWon(game);
    }

    private boolean mcHasWon(Game<A, ?> game) {
        double[] evaluation = game.getGameUtilityValue();
        double score = Util.scoreOutOfUtility(evaluation, this.playerId);
        if (!game.isGameOver() && score > 0.0D) {
            evaluation = game.getGameHeuristicValue();
            score = Util.scoreOutOfUtility(evaluation, this.playerId);
        }
        boolean win = (score == 1.0D);
        boolean tie = (score > 0.0D);
        win = (win || (tie && this.random.nextBoolean()));
        return win;
    }

    private void mcBackPropagation(Tree<McGameNode<A>> tree, boolean win) {
        int depth = 0;
        while (!tree.isRoot() && (depth++ % 31 != 0 || !shouldStopComputation())) {
            tree = tree.getParent();
            ((McGameNode)tree.getNode()).incPlays();
            if (win)
                ((McGameNode)tree.getNode()).incWins();
        }
    }

    private double upperConfidenceBound(Tree<McGameNode<A>> tree, double c) {
        double w = ((McGameNode)tree.getNode()).getWins();
        double n = Math.max(((McGameNode)tree.getNode()).getPlays(), 1);
        double N = n;
        if (!tree.isRoot())
            N = ((McGameNode)tree.getParent().getNode()).getPlays();
        return w / n + c * Math.sqrt(Math.log(N) / n);
    }

    public String toString() {
        if (this.instanceNr > 1 || INSTANCE_NR_COUNTER > 2)
            return String.format("%s%d", new Object[] { "MctsAgent#", Integer.valueOf(this.instanceNr) });
        return "MctsAgent";
    }
}
