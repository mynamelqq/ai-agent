package com.ai.aigent.agent;

import com.ai.aigent.agent.model.AgentEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public abstract class ReActAgent extends BaseAgent {
    public abstract boolean think();

    public abstract String act();

    @Override
    public String step() {
         List<AgentEvent> stepEvents = new ArrayList<>();
            try {
                if (think()) return act();
                return "思考完成-无需行动";
            } catch (Exception e) {
                return "步骤执行失败：" + e.getMessage();
            } finally {
                if (getOnStepFinished() != null) {
                    getOnStepFinished().accept(stepEvents);
                }
            }
    }
}
