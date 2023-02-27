import at.ac.tuwien.ifs.sge.agent.AbstractGameAgent;
import at.ac.tuwien.ifs.sge.agent.GameAgent;
import at.ac.tuwien.ifs.sge.engine.Logger;
import at.ac.tuwien.ifs.sge.game.risk.board.Risk;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskAction;
import at.ac.tuwien.ifs.sge.util.Util;
import at.ac.tuwien.ifs.sge.util.tree.Tree;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class HardDiskRisk extends AbstractGameAgent<Risk, RiskAction> implements GameAgent<Risk, RiskAction> {
    private static final int MAX_PRINT_THRESHOLD = 97;

    private static int INSTANCE_NR_COUNTER = 1;

    private final int instanceNr;

    private final double exploitationConstant;

    // The distribution of the troop in sub-phase 3.2 of selection phase
    private final int[] phase32Distr = new int[]{6,4,3,3};

    // The current selection territory when in sub-phase 3.2 of selection phase
    private int currentPhase32Terr = 0;

    private int phase32Counter = 0;

    // a flag for sub-phase 3.1 in the selection phase
    private boolean reinforceSwitch = true;

    NeuralNetwork nn;

    // A static logger for other classes
    static Logger staticLog;

    private MyDoubleLinkedTree mcTree;

    public static ArrayList<Set<Integer>> staticContinents = new ArrayList<>(Arrays.asList(new HashSet<Integer>(Arrays.asList(38,39,40,41)), new HashSet<Integer>(Arrays.asList(9,10,11,12)), new HashSet<Integer>(Arrays.asList(0,1,2,3,4,5,6,7,8)),
    new HashSet<Integer>(Arrays.asList(13,14,15,16,17,18,19)), new HashSet<Integer>(Arrays.asList(20,21,22,23,24,25)), new HashSet<Integer>(Arrays.asList(26,27,28,29,30,31,32,33,34,35,36,37))));
    public ArrayList<Set<Integer>> continents = new ArrayList<>();

    private int placedTroopsCounter = 0;

    private Set<RiskAction> selectionPhase = new HashSet<>();

    private Set<RiskAction> preferredStartingActionsAustralia = new HashSet<>();
    private Set<RiskAction> preferredStartingActionsSouthAmerica = new HashSet<>();
    private Set<Integer> australia = new HashSet<>();
    private Set<Integer> southAmerica = new HashSet<>();
    private Set<Integer> northAmerica = new HashSet<>();
    private Set<Integer> europe = new HashSet<>();
    private Set<Integer> africa = new HashSet<>();
    private Set<Integer> asia = new HashSet<>();

    // Prunes unwanted branches during selection phase
    private void cutTreeInSelectionPhase(MyDoubleLinkedTree moveTree, Set<RiskAction> prefs){

      Iterator<McGameNode> iterator = moveTree.myChildrenIterator();
      while (iterator.hasNext()) {
          if(!prefs.contains(iterator.next().getGame().getPreviousAction())){
              iterator.remove();
          }
      }
    }

    private boolean isSelectionPhase(Risk game){
        return placedTroopsCounter <= 50 && game.getBoard().isReinforcementPhase();
    }

    // custom setup, which creates a set of territoryIds of the countries in each continent and initializes the neural network
    private void customSetup(){
        staticLog = log;
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
            australia.add(tmpAus[i]);
            southAmerica.add(tmpSouth[i]);
            preferredStartingActionsAustralia.add(RiskAction.select(tmpAus[i]));
            preferredStartingActionsSouthAmerica.add(RiskAction.select(tmpSouth[i]));
        }

        continents.add(australia);
        continents.add(southAmerica);
        continents.add(northAmerica);
        continents.add(europe);
        continents.add(africa);
        continents.add(asia);

        // Initialize neural network
        nn = new NeuralNetwork();
        try {
            nn.init();
        } catch (Exception e) {
            log.debug(e);
            nn = null;
        }

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

    // Expands a leaf node
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

    // Returns the ratio of enemy countries for every continent besides southAfrica and australia
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
        return placedTroopsCounter <= 21;
    }

    // Checks whether a continent still has free territories
    private boolean hasFreeTerritories(Risk game, Set<Integer> continent, Set<RiskAction> freeTerritories){
        Set<RiskAction> freeTerritoriesInContinent = getFreeTerritoriesInContinent(freeTerritories, continent.stream().map(RiskAction::select).collect(Collectors.toSet()));
        return !freeTerritoriesInContinent.isEmpty();
    }

    private int counterAus = 0;
    private int counterAm = 0;

    //prunes the move tree in the selection phase
    //divides selection phase in 1, 2, 3.1 and 3.2 sub-phases
    //phase 1: only allows moves that claim territories in South America and Australia, if such moves exist
    //phase 2: is triggered when all territories in SouthA and Aus are claimed. Removes all moves, except those where troops are placed in the continent where the enemy has the highest percentage of territories
    //phase 3.1: all territories are claimed, reinforces borders in SouthA and Aus
    //phase 3.2: reinforces other countries in the same manner as in phase 2
    private void pruneMovesInSelectionPhase(Risk game){
        Set<RiskAction> freeTerritories = new HashSet<RiskAction>(selectionPhase);
        freeTerritories.retainAll(game.getPossibleActions());
        if (isSelectionPhase1or2()){
            // phase 1 or 2 of selectionPhase
            this.log.debug("Selecting preferred territory");

            // Determine free territories in south america and australia
            Set<RiskAction> freeSouthAmericaTerritories = getFreeTerritoriesInContinent(freeTerritories, preferredStartingActionsSouthAmerica);
            Set<RiskAction> freeAustraliaTerritories = getFreeTerritoriesInContinent(freeTerritories, preferredStartingActionsAustralia);

            if(!(freeAustraliaTerritories.isEmpty() && freeSouthAmericaTerritories.isEmpty())) {
                // phase 1 of selectionPhase
                if (freeSouthAmericaTerritories.isEmpty() || (counterAus <= counterAm && !freeAustraliaTerritories.isEmpty())) {
                    log.debug("sp1: Selecting from Australia");
                    cutTreeInSelectionPhase( mcTree, preferredStartingActionsAustralia);
                    counterAus++;
                } else {
                    log.debug("sp1: Selecting from South America");
                    cutTreeInSelectionPhase(mcTree , preferredStartingActionsSouthAmerica);
                    counterAm++;
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
                Set<Integer> freeTerritoryIDs = freeTerritories.stream().mapToInt(RiskAction::selected).boxed().collect(Collectors.toSet());
                Set<Integer> freeIDsInContinent = getFreeTerritoriesInContinentByID(freeTerritoryIDs, continents.get(maxIndex+2));
                cutTreeInSelectionPhase(mcTree , freeIDsInContinent.stream().map(RiskAction::select).collect(Collectors.toSet()));

            }
        } else {
            if (placedTroopsCounter <= 34){
                // phase 3.1 of selectionPhase
                this.log.debug("Selecting in phase 3.1");

                int troopsAus = 0;
                int troopsSouth = 0;

                for (int i = 0; i < australia.size(); i++) {
                    int[] positionsAustralia = australia.stream().mapToInt(a -> a).toArray();
                    if(game.getBoard().getTerritoryOccupantId(positionsAustralia[i]) == playerId ) {
                        troopsAus += game.getBoard().getTerritoryTroops(positionsAustralia[i]);
                    }
                }
                for (int i = 0; i < southAmerica.size(); i++) {
                    int[] positionsSouth = southAmerica.stream().mapToInt(a -> a).toArray();
                    if(game.getBoard().getTerritoryOccupantId(positionsSouth[i]) == playerId ) {
                        troopsSouth += game.getBoard().getTerritoryTroops(positionsSouth[i]);
                    }
                }
                Set<Integer> toReinforce = new HashSet<>();
                if(reinforceSwitch && game.getBoard().getTerritoryOccupantId(39) == playerId && game.getBoard().getTerritoryTroops(39) < 7){
                    toReinforce.add(39);
                    cutTreeInReinforcementPhase(toReinforce);
                } else if(reinforceSwitch && troopsAus < 9) {
                    cutTreeInReinforcementPhase(australia);
                } else if(!reinforceSwitch && game.getBoard().getTerritoryOccupantId(10) == playerId && game.getBoard().getTerritoryTroops(10) < 5) {
                    toReinforce.add(10);
                    toReinforce.remove(39);
                    cutTreeInReinforcementPhase(toReinforce);
                } else if(!reinforceSwitch && game.getBoard().getTerritoryOccupantId(12) == playerId && game.getBoard().getTerritoryTroops(12) < 5) {
                    toReinforce.add(12);
                    toReinforce.remove(10);
                    cutTreeInReinforcementPhase(toReinforce);
                } else if(!reinforceSwitch && troopsSouth < 10){
                    cutTreeInReinforcementPhase(southAmerica);
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

    // Prunes moves in order to only reinforce countries near the border to the enemy
    private void pruneReinforce(Risk game){

        Iterator<McGameNode> iterator = mcTree.myChildrenIterator();
        boolean notSkipped = false;
        while (iterator.hasNext()) {
            var temp = iterator.next();
            if (!notSkipped && !iterator.hasNext()) continue;
            if(!temp.getGame().getPreviousAction().toString().startsWith("C") && !temp.getGame().getPreviousAction().toString().startsWith("E") && temp.getGame().getPreviousAction().reinforcedId() >= 0 &&
                    temp.getGame().getPreviousAction().reinforcedId() <= 41 &&!checkIfReinforceable(game, temp.getGame().getPreviousAction().reinforcedId())){
                iterator.remove();
            } else {
                notSkipped = true;
            }
        }
    }

    private void reinforceContinent(Risk game, Set<Integer> continent){
        cutTreeInReinforcementPhase(continent.stream().filter(a->!game.getBoard().neighboringEnemyTerritories(a).isEmpty()).collect(Collectors.toSet()));
    }

    private void cutTreeInReinforcementPhase(Set<Integer> terrIds){
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
            log.debug("could not prune on reinforcement phase");
        }
    }
    //returns true when a country is near the border
    private boolean checkIfReinforceable(Risk game, int TerID) {

        if (game.getBoard().isFortifyPhase() && game.getBoard().getTerritoryOccupantId(TerID) != playerId) return false;

        if(TerID < 0 || TerID >= 42) return false;

        //checking if TerID is a border
        if (game.getBoard().neighboringEnemyTerritories(TerID).size() > 0) {
            return true;
            //checking if any neighboring territories of TerID are borders
        } else if (game.getBoard().neighboringFriendlyTerritories(TerID).stream()
                .filter(a -> game.getBoard().neighboringEnemyTerritories(a).size() > 0)
                .collect(Collectors.toSet()).size() > 0) return true;

        return false;
    }

    private double contactNeuralNetwork(String msg) {

        if (shouldStopComputation() || nn == null) return 0;

        try {
            return nn.predict(Arrays.stream(msg.split(", ")).mapToDouble(Double::parseDouble).toArray());
        } catch (Exception e) {
            log.debug(e);
            return 0;
        }
    }

    // Writes the data into Trainingdata.csv to train the neural network
    private void writeState(List<String> msges, double value){
        File file = new File("Trainingdata.csv");
        FileWriter writer = null;
        try {
            writer = new FileWriter(file, true);
            for (String msg: msges) {
                writer.write(msg + ", " + value + '\n');
            }
            writer.close();
        } catch (IOException ioe){
            System.err.println(ioe);
        }
    }


    boolean newTurn = true;
    private int defTemp = -1;
    private int attTemp = -1;

    // Encodes a given action to a string
    private String actionToString(Risk game, RiskAction action){
        String msg = "";
        if(action.isEndPhase()){
            msg += "1, 0, 0, 0, 0, 0, 0, -1, -1, -1";
        } else if (action.isBonus()){
            msg += "0, 1, 0, 0, 0, 0, 0, -1, -1, -1";
        } else if (action.isCardIds()){
            msg += "0, 0, 1, 0, 0, 0, 0, -1, -1, -1";
        } else if (game.getBoard().isOccupyPhase()){
            msg += "0, 0, 0, 1, 0, 0, 0, "+ attTemp +", " + defTemp + ", " + action.troops();
        } else if (game.getBoard().isReinforcementPhase()){
            msg += "0, 0, 0, 0, 1, 0, 0, "+ -1 +", " + action.reinforcedId() + ", " + action.troops();
        } else if (game.getBoard().isFortifyPhase()){
            msg += "0, 0, 0, 0, 0, 1, 0, "+ action.fortifyingId() +", " + action.fortifiedId() + ", " + action.troops();
        } else if (game.getBoard().isAttackPhase()){
            defTemp = action.defendingId();
            attTemp = action.attackingId();
            msg += "0, 0, 0, 0, 0, 0, 1, "+ action.attackingId() +", " + action.defendingId() + ", " + action.troops();
        } else {
            throw new NoSuchElementException("Unrecognized attack");
        }
        return msg;
    }

    // Encode current game state to a string
    private String buildString(Risk game, RiskAction action){
        StringBuilder msg = new StringBuilder("" + playerId);
        for (int i: game.getBoard().getTerritoryIds()){
            int temp = -1;
            if (game.getBoard().getTerritoryOccupantId(i) == playerId) temp = 1;
            msg.append(", ").append(game.getBoard().getTerritoryTroops(i) * temp);
        }
        int counter = 0;
        for (int i = 0; i < game.getBoard().getNumberOfCards(); i++) {
            if (game.getBoard().getPlayerCards(playerId).contains(i)) {
                msg.append(", ").append(i);
                counter++;
            };

        }
        for (int i = counter; i < 5; i++) {
            msg.append(", ").append(-1);
        }
        msg.append(", ").append(game.getBoard().getCardsLeft()).append(", ").append(game.getBoard().getTradeInBonus());
        msg.append(", ").append(game.getBoard().isReinforcementPhase() ? "1" : "0");
        msg.append(", ").append(game.getBoard().isAttackPhase() ? "1" : "0");
        msg.append(", ").append(game.getBoard().isOccupyPhase() ? "1" : "0");
        msg.append(", ").append(game.getBoard().isFortifyPhase() ? "1" : "0");
        msg.append(", ").append(mcTree.getNode().computeValue()).append(", ").append(actionToString(game, action));
        return msg.toString();
    }

    Queue<List<String>> messages = new ArrayDeque<>();
    List<String> current;

    // Collects training data for the neural network and writes it to a csv file
    private void printState(Risk game, RiskAction action){

        String msg = buildString(game, action);
        if (newTurn){
            current = new ArrayList<>();
            messages.add(current);
        }
        current.add(msg);
        if (messages.size() > 2){
            writeState(messages.poll(), mcTree.getNode().computeValue());
        }
    }

    public RiskAction computeNextAction(Risk game, long computationTime, TimeUnit timeUnit) {
        setTimers(computationTime, timeUnit);

        if (mcTree.getNode() == null){
            mcTree.setNode(new McGameNode(game, playerId));
        }

        this.log._tra("Searching for root of tree");
        boolean foundRoot = Util.findRoot(this.mcTree, game);
        if (foundRoot) {
            this.log._trace(", done.");
        } else {
            this.log._trace(", failed.");
        }

        exploreNode(mcTree);

        if (game.getCurrentPlayer() == playerId){
            placedTroopsCounter++;
        }
        this.log.info("HardDiskRisk playing now. Is op: " + game.getBoard().isOccupyPhase() + ". Is fp: " + game.getBoard().isFortifyPhase()+ ". Is ap: " + game.getBoard().isAttackPhase()+ ". Is rp: " + game.getBoard().isReinforcementPhase());
        if(isSelectionPhase(game)) {
            pruneMovesInSelectionPhase(game);
        }

        if (game.getBoard().isReinforcementPhase() && !isSelectionPhase(game)){
            pruneReinforce(game);
        }

        if (game.getBoard().isFortifyPhase()){
            prioritiseFortify(game);
        }

        System.gc();

       RiskAction nextAction = MCTSSearch();

        //printState(game, nextAction);

        if (nextAction.isEndPhase()){
            newTurn = true;
        } else {
            newTurn = false;
        }
        return nextAction;
    }

    //updates the priorities of the tree nodes in fortify phase, so that troops are moved away from territories that are not near a border.
    private void prioritiseFortify(Risk game) {
        for (Tree<McGameNode> child : mcTree.getChildren()) {
            if(!child.getNode().getGame().getPreviousAction().isEndPhase() && !checkIfReinforceable(game, child.getNode().getGame().getPreviousAction().fortifyingId()) && checkIfReinforceable(game, child.getNode().getGame().getPreviousAction().fortifiedId())){
                child.getNode().setPriority( 1 + child.getNode().getGame().getPreviousAction().troops() / 10.0);
            } else if (!child.getNode().getGame().getPreviousAction().isEndPhase() && !checkIfReinforceable(game, child.getNode().getGame().getPreviousAction().fortifyingId())){
                child.getNode().setPriority( 1 + child.getNode().getGame().getPreviousAction().troops() / 20.0);
            }
        }
    }

    // Predicts how favorable a move will turn out to be after two rounds.
    private double checkNodePrediction(McGameNode node) {

        double prediction = node.getPrediction();

        if (prediction < -0.5) {
            prediction = predictAction(node.getGame(), node.getGame().getPreviousAction());
            if (prediction == 0) prediction = node.computeValue();
            node.setPrediction(prediction);
        }
        return prediction;

    }

    // Predicts the benefit of a move in two rounds
    private double predictAction(Risk game, RiskAction action){
        String msg = buildString(game, action);
        return contactNeuralNetwork(msg);
    }

    // returns the best action for the next move at the end of the mcts
    private RiskAction getNextBest(){
        int maxPlays = mcTree.getChildren().stream().mapToInt(a->a.getNode().getPlays()).max().getAsInt();
        Set<Tree<McGameNode>> temp = mcTree.getChildren().stream().filter(a -> a.getNode().getPlays() >= maxPlays).collect(Collectors.toSet());
        int maxWins = temp.stream().mapToInt(a->a.getNode().getWins()).max().getAsInt();
        temp = temp.stream().filter(a -> a.getNode().getWins() >= maxWins).collect(Collectors.toSet());
        return temp.stream().reduce((a,b) -> a.getNode().getPrediction() > b.getNode().getPrediction() ? a : b).get().getNode().getGame().getPreviousAction();
    }

    // Executes the monte carlo tree search
    private RiskAction MCTSSearch() {
        while (!shouldStopComputation()) {
            MyDoubleLinkedTree tree = this.mcTree;
            tree = mcSelection(tree);
            exploreNode(tree);
            boolean won = mcSimulation(tree, 128, 2);
            mcBackPropagation(tree, won);
        }
        return getNextBest();
    }

    // Selects the next node based on the uct value, the priority and the prediction of the neural network
    private MyDoubleLinkedTree getBestUCT(List<MyDoubleLinkedTree> children){
        int ind = -1;
        double val = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < children.size(); i++) {
            boolean ourPlay = children.get(0).getNode().getGame().getCurrentPlayer() == playerId;
            double temp = upperConfidenceBound(children.get(i), this.exploitationConstant);
            temp *= ourPlay ? checkNodePrediction(children.get(i).getNode()) : 1 - checkNodePrediction(children.get(i).getNode());
            temp *= children.get(i).getNode().getPriority();
            if (ind == -1||(temp > val)){
                ind = i;
                val = temp;
            }
        }
        return children.get(ind);
    }

    private MyDoubleLinkedTree mcSelection(MyDoubleLinkedTree tree) {
        int depth = 0;
        while (!tree.isLeaf() && (depth++ % 31 != 0 || !shouldStopComputation())) {
            List<MyDoubleLinkedTree> children = new ArrayList<>(tree.myGetChildren());
            if (tree.getNode().getGame().getCurrentPlayer() < 0) {
                RiskAction action = tree.getNode().getGame().determineNextAction();
                for (MyDoubleLinkedTree child : children) {
                    if (child.getNode().getGame().getPreviousAction().equals(action))
                        tree = child;
                }
                continue;
            }
            tree = getBestUCT(tree.myGetChildren());
        }
        return tree;
    }

    private void mcBackPropagation(MyDoubleLinkedTree tree, boolean win) {

        int depth = 0;
        while (!tree.isRoot() && (depth++ % 31 != 0 || !shouldStopComputation())) {
            tree = tree.getParent();
            (tree.getNode()).incPlays();
            if (win)
                tree.getNode().incWins();
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
        return mcSimulation(tree, Long.MAX_VALUE);
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
        return score == 1 || (score != 0 && this.random.nextBoolean());
    }

}
