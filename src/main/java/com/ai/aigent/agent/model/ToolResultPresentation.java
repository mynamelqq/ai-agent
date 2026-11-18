package com.ai.aigent.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Extracts UI text without changing the raw result sent to the model. */
public record ToolResultPresentation(String displayResult, Boolean toolSuccess,
                                     String displayFormat, String language) {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_DISPLAY_LENGTH = 6000;

    public static ToolResultPresentation from(String toolName, String rawResult) {
        if (rawResult == null || rawResult.isBlank()) {
            return new ToolResultPresentation("", null, "text", null);
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(rawResult);
        } catch (Exception ignored) {
            return new ToolResultPresentation(limit(rawResult), null, "text", null);
        }
        if (root == null || !root.isObject()) {
            return new ToolResultPresentation(limit(root != null && root.isTextual()
                    ? root.asText() : rawResult), null, "text", null);
        }
        if ("runPythonInDocker".equals(toolName) || "execTerminal".equals(toolName)) {
            return execution(root, rawResult);
        }

        Boolean success = root.path("success").isBoolean() ? root.get("success").booleanValue() : null;
        String display = success == Boolean.FALSE ? firstText(root, "error", "message") : null;
        if (display == null) {
            display = firstText(root, "results", "content", "cosUrl", "url", "error", "message");
        }
        return new ToolResultPresentation(limit(display == null ? rawResult : display), success,
                "text", null);
    }

    public static ToolResultPresentation from(String toolName, String rawResult, String toolArguments) {
        ToolResultPresentation result = from(toolName, rawResult);
        boolean python = "runPythonInDocker".equals(toolName);
        boolean terminal = "execTerminal".equals(toolName);
        if (!python && !terminal) {
            return result;
        }
        try {
            JsonNode args = MAPPER.readTree(toolArguments);
            String code = text(args, python ? "pythonCode" : "command");
            if (code != null) {
                return new ToolResultPresentation(code, result.toolSuccess(), "code",
                        python ? "python" : "bash");
            }
        } catch (Exception ignored) {
            // Keep the execution result when the tool arguments are unavailable.
        }
        return result;
    }

    /** Add presentation fields to old tool-result payloads when history is read. */
    public static String enrichPayload(String payload) {
        return enrichPayload(payload, null);
    }

    public static String enrichPayload(String payload, String toolArguments) {
        try {
            JsonNode parsed = MAPPER.readTree(payload);
            if (!(parsed instanceof ObjectNode object)) {
                return payload;
            }
            String toolName = object.path("toolName").asText();
            boolean showInput = "runPythonInDocker".equals(toolName) || "execTerminal".equals(toolName);
            if (object.has("displayResult") && !(showInput && toolArguments != null)) {
                return payload;
            }
            JsonNode raw = object.get("toolResult");
            if (raw == null || !raw.isTextual()) {
                return payload;
            }
            ToolResultPresentation view = from(toolName, raw.asText(), toolArguments);
            object.put("displayResult", view.displayResult());
            object.put("displayFormat", view.displayFormat());
            if (view.language() != null) {
                object.put("language", view.language());
            }
            if (view.toolSuccess() == null) {
                object.putNull("toolSuccess");
            } else {
                object.put("toolSuccess", view.toolSuccess());
            }
            return MAPPER.writeValueAsString(object);
        } catch (Exception ignored) {
            return payload;
        }
    }

    private static ToolResultPresentation execution(JsonNode root, String rawResult) {
        if (!root.has("exitCode") && !root.has("stdout") && !root.has("stderr")) {
            return new ToolResultPresentation(limit(rawResult), null, "text", null);
        }
        JsonNode exitCode = root.path("exitCode");
        boolean timedOut = root.path("timedOut").asBoolean(false);
        boolean failed = timedOut || (exitCode.isNumber() && exitCode.asLong() != 0);
        Boolean success = timedOut || exitCode.isNumber() ? !failed : null;
        String stdout = text(root, "stdout");
        String stderr = text(root, "stderr");
        String display;
        if (failed) {
            String status = timedOut ? "执行超时" : "退出码 " + exitCode.asText();
            display = status + (stderr == null ? "" : "\n" + stderr)
                    + (stdout == null ? "" : "\n" + stdout);
        } else {
            display = stdout != null ? stdout : stderr != null ? stderr : "执行完成（无输出）";
        }
        return new ToolResultPresentation(limit(display), success, "text", null);
    }

    private static String firstText(JsonNode root, String... fields) {
        for (String field : fields) {
            String value = text(root, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode root, String field) {
        if (root == null) {
            return null;
        }
        JsonNode node = root.get(field);
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private static String limit(String value) {
        return value.length() <= MAX_DISPLAY_LENGTH ? value
                : value.substring(0, MAX_DISPLAY_LENGTH) + "\n…（展示已截断，可查看原始结果）";
    }
}
