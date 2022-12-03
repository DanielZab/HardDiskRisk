import at.ac.tuwien.ifs.sge.game.risk.board.Risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

public class MyTest {
    @Test
    public void text_example(){
        Risk exampleGame = new Risk();
        HardDiskRisk agent = new HardDiskRisk();
        assertEquals(3,3);
    }
}
