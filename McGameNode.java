import at.ac.tuwien.ifs.sge.game.Game;
import at.ac.tuwien.ifs.sge.game.risk.board.Risk;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskAction;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskBoard;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskCard;
import at.ac.tuwien.ifs.sge.util.node.GameNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class McGameNode implements GameNode<RiskAction> {
    private Risk game;

    private int wins;

    private int plays;

    private int playerID;

    public double getPriority() {
        return priority;
    }

    private double priority = 1;

    public void setPriority(double priority) {
        this.priority = priority;
    }

    public McGameNode(Risk game, int playerID) {
        this(game, 0, 0, playerID);
    }

    public McGameNode(Risk game, RiskAction action, int playerID) {
        this((Risk) game.doAction(action), playerID);
    }

    public McGameNode(Risk game, int wins, int plays, int playerID) {
        this.game = game;
        this.wins = wins;
        this.plays = plays;
        this.playerID = playerID;
    }

    public double prediction = -1.0;

    public Risk getGame() {
        return this.game;
    }

    @Override
    public void setGame(Game<RiskAction, ?> game) {
        if (game instanceof Risk) {
            setGame((Risk) game);
        }
    }

    public void setGame(Risk game) {
        this.game = game;
    }

    public int getWins() {
        return this.wins;
    }

    public void incWins() {
        this.wins++;
    }

    public int getPlays() {
        return this.plays;
    }

    public void incPlays() {
        this.plays++;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        McGameNode mcGameNode = (McGameNode) o;
        return (this.wins == mcGameNode.wins && this.plays == mcGameNode.plays && this.game.equals(mcGameNode.game));
    }

    public int hashCode() {
        return Objects.hash(this.game, this.wins, this.plays);
    }

    //sigmoid function to scale all received values between 0 and 1
    public static double sigmoid(double x, double x50L, double x50U, double ymin, double ymax) {
        double a = (x50L + x50U) / 2;
        double b = 2 / Math.abs(x50L - x50U);
        double c = ymin;
        double d = ymax - c;

        double y = c + (d / (1 + Math.pow(Math.E, b * (x - a))));
        return y;
    }

    // Gets a multiplier based on amount of continents in possession
    private double getContinentMultiplier(int playerID) {
        double tempMult = 1;
        for (Set<Integer> cont : HardDiskRisk.staticContinents) {
            int temp = cont.size();
            cont.retainAll(game.getBoard().getTerritoriesOccupiedByPlayer(playerID));
            if (cont.size() == temp) {
                tempMult += 0.5 + temp / 10.0;
            }
            ;
        }
        return tempMult;
    }

    // Gets the amount of troops a player has
    private int getTroopsOfPlayer(int i, RiskBoard board, ArrayList<Integer> troopArray) {
        int troops = 0;
        for (int terrId : board.getTerritoriesOccupiedByPlayer(i)) {
            if (board.getMobileTroops(terrId) > 0) {
                troops += board.getMobileTroops(terrId);
                troopArray.add(board.getTerritoryTroops(terrId));
            }
        }
        return troops;
    }

    // computes a custom value for the state of the game for this player
    // The result depends on the troops of the player, the distribution of those,
    // the number of territories occupied, the number of whole continents in possession and the number of cards.
    public double computeValue() {

        Risk game = this.game;
        if (game.getCurrentPlayer() < 0) {
            game = (Risk) game.doAction(game.determineNextAction());
        }

        int playerCount = game.getNumberOfPlayers();

        RiskBoard board = (game.getBoard());
        double value = 0;

        // Determine value for each player
        for (int i = 0; i < playerCount; i++) {

            // Count amount of mobile troops of player
            ArrayList<Integer> troopArray = new ArrayList<Integer>();
            int troops = getTroopsOfPlayer(i, game.getBoard(), troopArray);

            double standardDeviation = 1;
            if (troopArray.size() > 0) {
                // Determine mean and standard deviation of the distribution of the troups
                double mean = troopArray.stream().mapToDouble(a -> a).sum() / troopArray.size();
                standardDeviation = Math.sqrt(troopArray.stream().mapToDouble(a -> Math.pow(a - mean, 2)).sum());
            }

            // Assign negative multiplier if player is enemy, 1 otherwise
            int multiplier = (i == playerID) ? 1 : -1 / Math.max(playerCount - 1, 1);
            double troopMult = (((double) troops) / Math.max((troops + getTroopsOfPlayer(1 - i, game.getBoard(), new ArrayList<>())), 1));
            double terrMult = Math.pow(board.getNrOfTerritoriesOccupiedByPlayer(i) / 21.0, 2);

            value += getCardMult(i) * getContinentMultiplier(i) * multiplier * troopMult * terrMult / Math.sqrt(Math.max(standardDeviation, 1));
        }
        return Math.min(Math.max(sigmoid(-value, -0.85, 0.85, 0, 1), 0), 1);
    }

    // Get a multiplier based on the cards a player holds
    private double getCardMult(int playerID) {

        double mult = 1;

        List<RiskCard> currentCards = game.getBoard().getPlayerCards(playerID);

        int infCount = 0;
        int cavCount = 0;
        int artCount = 0;
        int joker = 0;


        for (RiskCard card : currentCards) {

            if (card.getCardType() == 0) {
                joker++;
            } else if (card.getCardType() == 1) {
                infCount++;
            } else if (card.getCardType() == 2) {
                artCount++;
            } else if (card.getCardType() == 3) {
                cavCount++;
            }
        }
        int bonus = game.getBoard().getTradeInBonus();
        int allTroops = getTroopsOfPlayer(0, game.getBoard(), new ArrayList<Integer>()) + getTroopsOfPlayer(1, game.getBoard(), new ArrayList<Integer>());
        if (allTroops == 0) allTroops = 1;
        if (infCount + joker >= 3 || cavCount + joker >= 3 || artCount + joker >= 3 || Math.min(joker, 1) + Math.min(infCount, 1) + Math.min(artCount, 1) + Math.min(cavCount, 1) >= 3) {
            mult += bonus / (double) allTroops;
        } else if (currentCards.size() > 0) {
            mult += (bonus / (double) allTroops) * currentCards.size() / 5;
        }
        return mult;
    }

    public double getPrediction() {
        return prediction;
    }
    public void setPrediction(double value){
        this.prediction = value;
    }

}

