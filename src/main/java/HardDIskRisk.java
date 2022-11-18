import at.ac.tuwien.ifs.sge.agent.*;
import at.ac.tuwien.ifs.sge.engine.Logger;
import at.ac.tuwien.ifs.sge.game.Game;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class HardDIskRisk <G extends Game<A, ?>, A> extends AbstractGameAgent<G, A> implements GameAgent<G, A> {


        public HardDIskRisk(Logger log){
            super(log);
        }

    @Override
    public A computeNextAction(G game, long computationTime, TimeUnit timeUnit) {
        //optionally set AbstractGameAgent timers
        super.setTimers(computationTime, timeUnit);
        //choose the first option
        return List.copyOf(game.getPossibleActions()).get(0);
    }
}
