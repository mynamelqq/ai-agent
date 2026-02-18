package com.ai.aigent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public abstract class ReActAgent extends BaseAgent {
    public abstract boolean think();

    public abstract String act();

    @Override
    public String step() {
        try {
             if(think()) {
                 return act();
            }
            return "思考完成-无需行动";
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return"步骤执行失败："+e.getMessage();
        }


    }
}
