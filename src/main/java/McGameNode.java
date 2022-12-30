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
}

