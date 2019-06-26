package ch.uzh.ifi.access.student.evaluation.process;

import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory cache of state machines used for the ACCESS MVP.
 * This should be replaced with a persistent version for later use.
 */
@Service
public class EvalMachineRepoService {

    private Map<String, StateMachine> machines;

    public EvalMachineRepoService() {
        this.machines = new HashMap<>();
    }

    public StateMachine get(String key) {
        return machines.get(key);
    }

    public void store(String key, StateMachine machine) {
         machines.put(key, machine);
    }

}
