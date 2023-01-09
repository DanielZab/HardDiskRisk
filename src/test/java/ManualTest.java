import at.ac.tuwien.ifs.sge.engine.Logger;
import at.ac.tuwien.ifs.sge.game.risk.board.Risk;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskAction;

import java.util.concurrent.TimeUnit;

public class ManualTest {
    public static void main(String[] args) {
        Risk exampleGame = new Risk();
        HardDiskRisk agent = new HardDiskRisk(new Logger(-2, "[sge ", "",
                "trace]: ", System.out, "",
                "debug]: ", System.out, "",
                "info]: ", System.out, "",
                "warn]: ", System.err, "",
                "error]: ", System.err, ""));
        agent.setUp(2, 1);
        while(true){
            RiskAction action = agent.computeNextAction(exampleGame, 30, TimeUnit.SECONDS);
            exampleGame = (Risk) exampleGame.doAction(action);
        }

    }
}
