import at.ac.tuwien.ifs.sge.agent.AbstractGameAgent;
import at.ac.tuwien.ifs.sge.agent.GameAgent;
import at.ac.tuwien.ifs.sge.engine.Logger;
import at.ac.tuwien.ifs.sge.game.risk.board.Risk;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskAction;
import at.ac.tuwien.ifs.sge.util.Util;
import at.ac.tuwien.ifs.sge.util.tree.Tree;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class HardDiskRisk extends AbstractGameAgent<Risk, RiskAction> implements GameAgent<Risk, RiskAction> {
    private static final int MAX_PRINT_THRESHOLD = 97;

    private static int INSTANCE_NR_COUNTER = 1;

    private final int instanceNr;

    private final double exploitationConstant;

    private final int[] phase32Distr = new int[]{6,4,3,3};

    private int currentPhase32Terr = 0;

    private int phase32Counter = 0;

    private boolean reinforceSwitch = true;


/*
    private Comparator<Tree<McGameNode>> gameMcTreeUCTComparator;

    private Comparator<Tree<McGameNode>> gameMcTreeSelectionComparator;

    private Comparator<Tree<McGameNode>> gameMcTreePlayComparator;

    private Comparator<McGameNode> gameMcNodePlayComparator;

    private Comparator<Tree<McGameNode>> gameMcTreeWinComparator;

    private Comparator<McGameNode> gameMcNodeWinComparator;

    private Comparator<Tree<McGameNode>> gameMcTreeMoveComparator;

    private Comparator<McGameNode> gameMcNodeMoveComparator;

    private Comparator<McGameNode> gameMcNodeGameComparator;

    private Comparator<Tree<McGameNode>> gameMcTreeGameComparator;
*/


    private MyDoubleLinkedTree mcTree;

    ArrayList<Set<Integer>> continents = new ArrayList<>();

    // Custom variables and functions

    private int placedTroupsCounter = 0;

    private Set<RiskAction> selectionPhase = new HashSet<>();

    private Set<Integer> preferredStartingPositionsAustralia = new HashSet<>();
    private Set<Integer> preferredStartingPositionsSouthAmerica = new HashSet<>();

    private Set<RiskAction> preferredStartingActionsAustralia = new HashSet<>();
    private Set<RiskAction> preferredStartingActionsSouthAmerica = new HashSet<>();

    private Set<Integer> northAmerica = new HashSet<>();
    private Set<Integer> europe = new HashSet<>();
    private Set<Integer> africa = new HashSet<>();
    private Set<Integer> asia = new HashSet<>();

    private void selectPreferredTerritory(Risk game, MyDoubleLinkedTree moveTree, Set<RiskAction> prefs){

      Iterator<McGameNode> iterator = moveTree.myChildrenIterator();
        System.out.println(iterator.hasNext());
      while (iterator.hasNext()) {
          if(!prefs.contains(iterator.next().getGame().getPreviousAction())){
              iterator.remove();
          }
      }
        System.out.println(moveTree.getChildren().size());

    }

    private int countPreferredTerritories(Set<Integer> list, Set<Integer> territories){
        int counter = 0;
        for (Integer i: list) {
            if (territories.contains(i)) counter++;
        }
        return counter;
    }

    private boolean isSelectionPhase(Risk game){
        //return selectionPhase.contains(game.getPossibleActions().toArray()[0]);
        return placedTroupsCounter <= 50 && game.getBoard().isReinforcementPhase();
    }

    private void customSetup(){
        for (int i = 0; i < 42; i++) {
            selectionPhase.add(RiskAction.select(i));
        }

        for (int i = 0; i < 9; i++) {
            northAmerica.add(i);
        }
        for (int i = 13; i < 20; i++) {
            europe.add(i);
        }
        for (int i = 20; i < 26; i++) {
            africa.add(i);
        }
        for (int i = 26; i < 38; i++) {
            asia.add(i);
        }

        int[] tmpAus = {38,39,40,41};
        int[] tmpSouth = {9,10,11,12};

        for (int i = 0; i < tmpAus.length; i++) {
            preferredStartingPositionsAustralia.add(tmpAus[i]);
            preferredStartingPositionsSouthAmerica.add(tmpSouth[i]);
            preferredStartingActionsAustralia.add(RiskAction.select(tmpAus[i]));
            preferredStartingActionsSouthAmerica.add(RiskAction.select(tmpSouth[i]));
        }

        continents.add(preferredStartingPositionsAustralia);
        continents.add(preferredStartingPositionsSouthAmerica);
        continents.add(northAmerica);
        continents.add(europe);
        continents.add(africa);
        continents.add(asia);
    }

    public HardDiskRisk() {
        this((Logger)null);
    }

    public HardDiskRisk(Logger log) {
        this(Math.sqrt(2.0D), log);
    }

    public HardDiskRisk(double exploitationConstant, Logger log) {
        super(log);
        this.exploitationConstant = exploitationConstant;
        this.mcTree = new MyDoubleLinkedTree();
        this.instanceNr = INSTANCE_NR_COUNTER++;
    }

    public void setUp(int numberOfPlayers, int playerId){
        super.setUp(numberOfPlayers, playerId);
        this.mcTree.clear();
        customSetup();
    }

    public void exploreNode(MyDoubleLinkedTree node){
        if (node.isLeaf()) {
            Risk game = ((McGameNode)node.getNode()).getGame();
            Set<RiskAction> possibleActions = game.getPossibleActions();
            for (RiskAction possibleAction : possibleActions){
                node.add(new McGameNode(game, possibleAction, playerId));
            }
        }
    }

    private Set<RiskAction> getFreeTerritoriesInContinent(Set<RiskAction> freeTerritories, Set<RiskAction> contIds){
        Set<RiskAction> temp = new HashSet<>(freeTerritories);
        temp.retainAll(contIds);
        return temp;
    }
    private Set<Integer> getFreeTerritoriesInContinentByID(Set<Integer> freeTerritories, Set<Integer> contIds){
        Set<Integer> temp = new HashSet<>(freeTerritories);
        temp.retainAll(contIds);
        return temp;
    }

    private double[] getPercent(Risk game, int toRemove){
        int[] occupiedTerrs = new int[]{0, 0, 0, 0};
        for (int i = 0; i < game.getNumberOfPlayers(); i++) {
            if (i == playerId) continue;
            for (int terrId: game.getBoard().getTerritoriesOccupiedByPlayer(i)) {
                if(northAmerica.contains(terrId)){
                    occupiedTerrs[0]++;
                } else if (europe.contains(terrId)){
                    occupiedTerrs[1]++;
                } else if (africa.contains(terrId)){
                    occupiedTerrs[2]++;
                } else if (asia.contains(terrId)){
                    occupiedTerrs[3]++;
                }
            }
        }
        double[] percent = new double[4];
        percent[0] = occupiedTerrs[0] / (double) northAmerica.size();
        percent[1] = occupiedTerrs[1] / (double) europe.size();
        percent[2] = occupiedTerrs[2] / (double) africa.size();
        percent[3] = occupiedTerrs[3] / (double) asia.size();

        while(toRemove > 0){
            int maxInd = -1;
            double maxVal = 0;
            for (int i = 0; i < 4; i++) {
                if (maxVal < percent[i]){
                    maxVal = percent[i];
                    maxInd = i;
                }
            }
            if (maxInd != -1){
                percent[maxInd] = 0;
            }
            toRemove--;
        }
        return percent;
    }

    private boolean isSelectionPhase1or2(){
        return placedTroupsCounter <= 21;
    }

    private boolean hasFreeTerritories(Risk game, Set<Integer> continent, Set<RiskAction> freeTerritories){
        Set<RiskAction> freeTerritoriesInContinent = getFreeTerritoriesInContinent(freeTerritories, continent.stream().map(RiskAction::select).collect(Collectors.toSet()));
        return !freeTerritoriesInContinent.isEmpty();
    }

    private void pruneMovesInSelectionPhase(Risk game){
        Set<RiskAction> freeTerritories = new HashSet<RiskAction>(selectionPhase);
        freeTerritories.retainAll(game.getPossibleActions());
        if (isSelectionPhase1or2()){
            // phase 1 or 2 of selectionPhase
            this.log.debug("Selecting preferred territory");
            var territories = (game.getBoard()).getTerritoriesOccupiedByPlayer(this.playerId);

            // Determine free territories in south america and australia
            Set<RiskAction> freeSouthAmericaTerritories = getFreeTerritoriesInContinent(freeTerritories, preferredStartingActionsSouthAmerica);
            Set<RiskAction> freeAustraliaTerritories = getFreeTerritoriesInContinent(freeTerritories, preferredStartingActionsAustralia);
            freeSouthAmericaTerritories.retainAll(preferredStartingActionsSouthAmerica);
            System.out.println("Aus" + preferredStartingPositionsAustralia.size() + "Am" + preferredStartingPositionsSouthAmerica.size());
            if(!(freeAustraliaTerritories.isEmpty() && freeSouthAmericaTerritories.isEmpty())) {
                // phase 1 of selectionPhase
                if (freeSouthAmericaTerritories.isEmpty() || (countPreferredTerritories(preferredStartingPositionsAustralia, territories) < countPreferredTerritories(preferredStartingPositionsSouthAmerica, territories) && !freeAustraliaTerritories.isEmpty())) {
                    log.debug("sp1: Selecting from Australia");
                    selectPreferredTerritory(game, mcTree, preferredStartingActionsAustralia);
                } else {
                    log.debug("sp1: Selecting from South America");
                    selectPreferredTerritory(game, mcTree , preferredStartingActionsSouthAmerica);
                }
            } else {
                // phase 2 of selectionPhase
                this.log.debug("Selecting in phase 2");
                double[] percent = getPercent(game, 0);

                // select territory from continent with ID maxIndex
                int maxIndex = -1;
                for (int i = 0; i < percent.length; i++) {
                    if((maxIndex == -1 || percent[i]>percent[maxIndex]) && hasFreeTerritories(game, continents.get(i+2), freeTerritories)){
                        maxIndex = i;
                    }
                }
                // game.getBoard().getTerritoryOccupantId() == -1

                Set<Integer> freeTerritoryIDs = freeTerritories.stream().mapToInt(RiskAction::selected).boxed().collect(Collectors.toSet());
                Set<Integer> freeIDsInContinent = getFreeTerritoriesInContinentByID(freeTerritoryIDs, continents.get(maxIndex+2));
                log.debug("Selecting from continent: " + maxIndex);
                selectPreferredTerritory(game, mcTree , freeIDsInContinent.stream().map(RiskAction::select).collect(Collectors.toSet()));

            }
        } else {
            if (placedTroupsCounter <= 34){
                this.log.debug("Selecting in phase 3.1");
                // phase 3.1 of selectionPhase

                Set<RiskAction> reinforceBorder = new HashSet<>();
                int troopsAus = 0;
                int troopsSouth = 0;

                for (int i = 0; i < preferredStartingPositionsAustralia.size(); i++) {
                    int[] positionsAustralia = preferredStartingPositionsAustralia.stream().mapToInt(a -> a).toArray();
                    if(game.getBoard().getTerritoryOccupantId(positionsAustralia[i]) == playerId ) {
                        troopsAus += game.getBoard().getTerritoryTroops(positionsAustralia[i]);
                    }
                }
                for (int i = 0; i < preferredStartingPositionsSouthAmerica.size(); i++) {
                    int[] positionsSouth = preferredStartingPositionsSouthAmerica.stream().mapToInt(a -> a).toArray();
                    if(game.getBoard().getTerritoryOccupantId(positionsSouth[i]) == playerId ) {
                        troopsSouth += game.getBoard().getTerritoryTroops(positionsSouth[i]);
                    }
                }
                Set<Integer> toReinforce = new HashSet<>();
                if(reinforceSwitch && game.getBoard().getTerritoryOccupantId(39) == playerId && game.getBoard().getTerritoryTroops(39) < 7){
                    toReinforce.add(39);
                    reinforceTerritories(game, toReinforce);
                } else if(reinforceSwitch && troopsAus < 9) {
                    reinforceTerritories(game, preferredStartingPositionsAustralia);
                } else if(!reinforceSwitch && game.getBoard().getTerritoryOccupantId(10) == playerId && game.getBoard().getTerritoryTroops(10) < 5) {
                    toReinforce.add(10);
                    toReinforce.remove(39);
                    reinforceTerritories(game, toReinforce);
                } else if(!reinforceSwitch && game.getBoard().getTerritoryOccupantId(12) == playerId && game.getBoard().getTerritoryTroops(12) < 5) {
                    toReinforce.add(12);
                    toReinforce.remove(10);
                    reinforceTerritories(game, toReinforce);
                } else if(!reinforceSwitch && troopsSouth < 10){
                    reinforceTerritories(game, preferredStartingPositionsSouthAmerica);
                }
                reinforceSwitch = !reinforceSwitch;
            } else {
                this.log.debug("Selecting in phase 3.2");
                // phase 3.2 of selectionPhase
                double[] percent = getPercent(game, currentPhase32Terr);
                // select territory from continent with ID maxIndex
                int maxIndex = -1;
                for (int i = 0; i < percent.length; i++) {
                    if((maxIndex == -1 || percent[i]>percent[maxIndex]) && percent[i] < 0.99){
                        maxIndex = i;
                    }
                }

                if(maxIndex != -1){
                    if (phase32Distr[currentPhase32Terr] <= ++phase32Counter){
                        currentPhase32Terr++;
                        phase32Counter = 0;
                    }
                    reinforceContinent(game, continents.get(maxIndex+2));
                }

            }

        }
    }

    private void reinforceContinent(Risk game, Set<Integer> continent){
        reinforceTerritories(game, continent.stream().filter(a->!game.getBoard().neighboringEnemyTerritories(a).isEmpty()).collect(Collectors.toSet()));
    }
    private void reinforceTerritories(Risk game, Set<Integer> terrIds){
        Set<Integer> temp  = new HashSet<>(mcTree.getChildrenByIdWhenReinforcing());
        temp.retainAll(terrIds);
        if (!temp.isEmpty()){
            Iterator<McGameNode> iterator = mcTree.myChildrenIterator();

            while (iterator.hasNext()) {

                if(!temp.contains(iterator.next().getGame().getPreviousAction().reinforcedId())){
                    iterator.remove();
                }
            }
        } else {
            log.debug("could not prune on reinforce phase");
        }
    }

    public RiskAction computeNextAction(Risk game, long computationTime, TimeUnit timeUnit) {
        setTimers(computationTime, timeUnit);

        if (mcTree.getNode() == null){
            mcTree.setNode(new McGameNode(game, playerId));
        }

        this.log._tra("Searching for root of tree");
        boolean foundRoot = Util.findRoot(this.mcTree, (Risk)game);
        if (foundRoot) {
            this.log._trace(", done.");
        } else {
            this.log._trace(", failed.");
        }

        if (true){
            exploreNode(mcTree);
        }

        if (game.getCurrentPlayer() == playerId){
            placedTroupsCounter++;
        }
        this.log.info("HardDiskRisk playing now. Is op: " + game.getBoard().isOccupyPhase() + ". Is fp: " + game.getBoard().isFortifyPhase()+ ". Is ap: " + game.getBoard().isAttackPhase()+ ". Is rp: " + game.getBoard().isReinforcementPhase());

        if(isSelectionPhase(game)) {
            pruneMovesInSelectionPhase(game);
        }
        for (var x:mcTree.getChildren()) {
            System.out.println(x);
        }

        return MCTSSearch();

        /*
        if(mcTree.getChildren().isEmpty()){
            log.error("No children in tree");
        }
        return mcTree.getChildren().get(random.nextInt(mcTree.getChildren().size())).getNode().getGame().getPreviousAction();

         */
    }

    private RiskAction MCTSSearch() {
        while (!shouldStopComputation()) {
            MyDoubleLinkedTree tree = this.mcTree;
            tree = mcSelection(tree);
            exploreNode(tree);
            boolean won = mcSimulation(tree, 128, 2);
            mcBackPropagation(tree, won);
        }
        return getBestUCT(mcTree.myGetChildren(), true).getNode().getGame().getPreviousAction();
    }

//    private boolean mcSimulation(MyDoubleLinkedTree tree, int i, int i1) {
//    }

    private MyDoubleLinkedTree getBestUCT(List<MyDoubleLinkedTree> children, boolean useOwn){
        int ind = 0;
        double val = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < children.size(); i++) {
            double temp = upperConfidenceBound(children.get(i), this.exploitationConstant);
            temp += useOwn ? children.get(i).getNode().computeValue() : 0;
            if (temp > val){
                ind = i;
                val = temp;
            }
        }
        return children.get(ind);
    }

    private MyDoubleLinkedTree mcSelection(MyDoubleLinkedTree tree) {
        int depth = 0;
        while (!tree.isLeaf() && (depth++  % 31 != 0 || !shouldStopComputation())) {
            List<MyDoubleLinkedTree> children = new ArrayList<>(tree.myGetChildren());
            if (tree.getNode().getGame().getCurrentPlayer() < 0) {
                RiskAction action = tree.getNode().getGame().determineNextAction();
                for (MyDoubleLinkedTree child : children) {
                    if (child.getNode().getGame().getPreviousAction().equals(action))
                        tree = child;
                }
                continue;
            }
            tree = getBestUCT(tree.myGetChildren(), true);
        }
        return tree;
    }

    private void mcBackPropagation(MyDoubleLinkedTree tree, boolean win) {

        int depth = 0;
        while (!tree.isRoot() && (depth++ % 31 != 0 || !shouldStopComputation())) {
            tree = tree.getParent();
            (tree.getNode()).incPlays();
            if (win)
                (tree.getNode()).incWins();
        }
    }

    private double upperConfidenceBound(MyDoubleLinkedTree tree, double c) {
        double w = tree.getNode().getWins();
        double n = Math.max((tree.getNode()).getPlays(), 1);
        double N = n;
        if (!tree.isRoot())
            N = tree.getParent().getNode().getPlays();
        return w / n + c * Math.sqrt(Math.log(N) / n);
    }

    private boolean mcSimulation(MyDoubleLinkedTree tree, int simulationsAtLeast, int proportion) {
        int simulationsDone = (tree.getNode()).getPlays();
        if (simulationsDone < simulationsAtLeast && shouldStopComputation(proportion)) {
            int simulationsLeft = simulationsAtLeast - simulationsDone;
            return mcSimulation(tree, nanosLeft() / simulationsLeft);
        }
        if (simulationsDone == 0)
            return mcSimulation(tree, this.TIMEOUT / 2L - nanosElapsed());
        return mcSimulation(tree);
    }

    private boolean mcSimulation(MyDoubleLinkedTree tree) {
        Risk game = (tree.getNode()).getGame();
        int depth = 0;
        while (!game.isGameOver() && (depth++ % 31 != 0 || !shouldStopComputation())) {
            if (game.getCurrentPlayer() < 0) {
                game = (Risk) game.doAction();
                continue;
            }
            game = (Risk) game.doAction(Util.selectRandom(game.getPossibleActions(), this.random));
        }
        return mcHasWon(game);
    }

    private boolean mcSimulation(MyDoubleLinkedTree tree, long timeout) {
        long startTime = System.nanoTime();
        Risk game = (tree.getNode()).getGame();
        int depth = 0;
        while (!game.isGameOver() && System.nanoTime() - startTime <= timeout && (depth++ % 31 != 0 ||
                !shouldStopComputation())) {
            if (game.getCurrentPlayer() < 0) {
                game = (Risk) game.doAction();
                continue;
            }
            game = (Risk) game.doAction(Util.selectRandom(game.getPossibleActions(), this.random));
        }
        return mcHasWon(game);
    }

    private boolean mcHasWon(Risk game) {
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

    public void tearDown() {
    }

    public void destroy() {
    }

    /*
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
        customSetup();
    }

    public A computeNextAction(G game, long computationTime, TimeUnit timeUnit) {
        setTimers(computationTime, timeUnit);

        placedTroupsCounter++;
        this.log.info("HardDiskRisk playing now");

        if(isSelectionPhase(game)) {

            var territories = ((RiskBoard)game.getBoard()).getTerritoriesOccupiedByPlayer(this.playerId);
            A action = null;
            if (countPreferredTerritories(preferredStartingPositionsAustralia, territories) < countPreferredTerritories(preferredStartingPositionsSouthAmerica, territories)){
                action = selectPreferredTerritory(game, preferredStartingPositionsAustralia);
            }
            if (action != null) return action;
            action = selectPreferredTerritory(game, preferredStartingPositionsSouthAmerica);
            if (action != null) return action;

        }
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
        while (!tree.isLeaf() && (depth++  % 31 != 0 || !shouldStopComputation())) {
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
    */
}
