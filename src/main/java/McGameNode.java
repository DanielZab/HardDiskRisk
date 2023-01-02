import at.ac.tuwien.ifs.sge.game.Game;
import at.ac.tuwien.ifs.sge.game.risk.board.Risk;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskAction;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskBoard;
import at.ac.tuwien.ifs.sge.util.node.GameNode;

import java.util.ArrayList;
import java.util.Objects;

public class McGameNode implements GameNode<RiskAction> {
    private Risk game;

    private int wins;

    private int plays;

    private boolean explored;

    private int playerID;

    public McGameNode(int playerID) {
        this(null, playerID);
    }

    public McGameNode(Risk game, int playerID) {
        this(game, 0, 0, playerID);
    }

    public McGameNode(Risk game, RiskAction action, int playerID) {
        this((Risk)game.doAction(action),playerID);
    }

    public McGameNode(Risk game, int wins, int plays, int playerID) {
        this.game = game;
        this.wins = wins;
        this.plays = plays;
        this.playerID = playerID;
        this.explored = false;
    }

    public Risk getGame() {
        return this.game;
    }

    public void setExplored(){
        explored = true;
    }

    @Override
    public void setGame(Game<RiskAction, ?> game) {
        if (game instanceof Risk){
            setGame((Risk) game);
        }
    }

    public boolean isExplored() {
        return explored;
    }

    public void setGame(Risk game) {
        this.game = game;
    }

    public int getWins() {
        return this.wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public void incWins() {
        this.wins++;
    }

    public int getPlays() {
        return this.plays;
    }

    public void setPlays(int plays) {
        this.plays = plays;
    }

    public void incPlays() {
        this.plays++;
    }

    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        McGameNode mcGameNode = (McGameNode)o;
        return (this.wins == mcGameNode.wins && this.plays == mcGameNode.plays && this.game.equals(mcGameNode.game));
    }

    public int hashCode() {
        return Objects.hash(new Object[] { this.game, Integer.valueOf(this.wins), Integer.valueOf(this.plays) });
    }

    /*
        determines and quantifies the situation for our player
    */
    public double computeValue() {

        int playerCount = game.getNumberOfPlayers();

        RiskBoard board = ((RiskBoard)game.getBoard());
        double value = 0;

        // Determine value for each player
        for (int i = 0; i < playerCount; i++) {

            // Count amount of troups of player
            int troups = 0;
            ArrayList<Integer> troupArray = new ArrayList<Integer>();
            for (int terrId:board.getTerritoriesOccupiedByPlayer(i)) {
                troups += board.getTerritoryTroops(terrId);
                troupArray.add(board.getTerritoryTroops(terrId));
            }

            // Determine mean and standard deviation of the distribution of the troups
            double mean = troupArray.stream().mapToDouble(a -> a).sum() / troupArray.size();
            double standardDeviation = Math.sqrt(troupArray.stream().mapToDouble(a -> Math.pow(a - mean, 2)).sum());

            // Assign negative multiplier if player is enemy, 1 otherwise
            int multiplier = (i == playerID) ? 1 : -1/ Math.max(playerCount-1, 1);

            value += multiplier * troups * board.getNrOfTerritoriesOccupiedByPlayer(i) / Math.max(standardDeviation, 1);
        }

        return value / 8400;
    }

}

