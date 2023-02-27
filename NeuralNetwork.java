import ai.onnxruntime.*;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public class NeuralNetwork {
    OrtEnvironment env;
    OrtSession session;

    // Initializes the neural network
    public void init() throws OrtException, IOException {
        env = OrtEnvironment.getEnvironment();
        var x = HardDiskRisk.class.getClassLoader().getResourceAsStream("model.onnx");
        this.session = env.createSession(x.readAllBytes());
    }

    // Gets the prediction for a given dataset
    public double predict(double[] values) throws OrtException {

        Map<String, OnnxTensor> map = new TreeMap<>();

        float[] newValues = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            newValues[i] = (float) values[i];
        }

        OnnxTensor inputTensor = OnnxTensor.createTensor(env, new float[][]{newValues});

        map.put("normalization_input", inputTensor);
        OrtSession.Result outputTensor = session.run(map);

        return ((float[][]) (outputTensor.get(0).getValue()))[0][0];
    }
}
