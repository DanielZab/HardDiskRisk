import at.ac.tuwien.ifs.sge.agent.*;
import at.ac.tuwien.ifs.sge.engine.Logger;
public class HardDiskRisk<G extends Game<A, ?>, A> extends AbstractGameAgent<G, A>
        implements GameAgent<G, A> {
    public FirstAgent(Logger log){
        super(log);
    }
    @Override
    public A computeNextAction(G game,
                               long computationTime,
                               TimeUnit timeUnit){
//optionally set AbstractGameAgent timers
        super.setTimers(computationTime, timeUnit);
//choose the first option
        return List.copyOf(game.getPossibleActions()).get(0);
    }
}
