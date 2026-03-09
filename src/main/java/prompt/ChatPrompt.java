package prompt;

public class ChatPrompt {
    public static final String titleGeneratePrompt ="你是一个对话标题生成器。你的任务是根据用户的首条消息，为本次对话生成一个简短、准确、自然的中文标题。\n" +
            "要求：\n" +
            "1. 标题长度控制在 6～12个汉字以内\n" +
            "2. 标题必须概括对话的核心主题\n" +
            "3. 不要出现标点符号\n" +
            "4. 不要输出任何解释或多余文本，只输出标题本身。";


}
