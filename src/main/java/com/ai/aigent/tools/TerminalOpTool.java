package com.ai.aigent.tools;

import com.ai.aigent.constant.ExecErrorType;
import com.ai.aigent.entity.dto.TerminalExecResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TerminalOpTool {
    private static final String WORKSPACE = "E:/AAA project/aigent/aigent/tmp/file";
      private final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALLOWED_COMMAND_PREFIX = List.of(
            "ls",
            "pwd","dir","cd",
            "whoami",
            "cat",
            "echo",
            "java",
            "python",
            "node",
            "npm",
            "git",
            "mvn",
            "gradle",
            "find","file","grep","wc","head","tail","date","uptime"
    );
        @Tool(description = "在受限Windows环境中执行终端命令,,并返回执行结果,终端工具的目录位于E:\\AAA project\\aigent\\aigent\\src\\main\\java\\com.ai.aigent\\tools")
    public String execTerminal(
            @ToolParam(description = "要执行的终端命令（仅支持白名单命令）") String command) {

        if (command == null || command.isBlank()) {
            return toJson(TerminalExecResponseDTO.error(null, "command 不能为空",ExecErrorType.UNKNOWN));
        }

        command = command.trim();

        // 1️⃣ 安全校验
        if (!isCommandAllowed(command)) {
            return toJson(TerminalExecResponseDTO.error(command, "命令不被允许",ExecErrorType.PERMISSION_DENIED));
        }

        try {
            ProcessBuilder builder=new ProcessBuilder();

            // Linux / Mac
            builder.command("cmd.exe","/c", "cd /d \"E:\\AAA project\\aigent\\aigent\\tmp\\file\" && " +command);//bash", "-c

            builder.redirectErrorStream(false);

            Process process = builder.start();

            // 2️⃣ 超时控制
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                ExecErrorType errorType = ExecErrorType.TIMEOUT;
                process.destroyForcibly();
                return toJson(TerminalExecResponseDTO.error(command, "命令执行超时",errorType));
            }

            // 3️⃣ 读取输出
            String stdout = readStream(process.getInputStream(), 3000);
            String stderr = readStream(process.getErrorStream(), 2000);

            int exitCode = process.exitValue();
            ExecErrorType errorType = classifyError(stderr);
            return toJson(
                    TerminalExecResponseDTO.ok(command, stdout, stderr, exitCode,errorType)
            );

        } catch (Exception e) {
            return "终端工具执行失败:"+toJson(
                    TerminalExecResponseDTO.error(command, e.getMessage(),ExecErrorType.UNKNOWN)
            );
        }
    }
    private boolean isCommandAllowed(String command) {

        // 禁止危险符号
//        if (
//                command.contains(";")
//                || command.contains(">")
//                || command.contains("<")) {
//            return false;
//        }

        String baseCmd = command.split("\\s+")[0];
        return ALLOWED_COMMAND_PREFIX.contains(baseCmd);
    }
       private String readStream(InputStream inputStream, int maxChars)
            throws IOException {

        Charset charset = Charset.forName("GBK");

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(inputStream, charset))) {

            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
                if (sb.length() > maxChars) {
                    sb.append("... [output truncated]");
                    break;
                }
            }
        }
        return sb.toString();
    }
     private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"JSON序列化失败\"}";
        }
    }
    private ExecErrorType classifyError(String stderr) {
        if (stderr == null||stderr.isEmpty()) return ExecErrorType.NONE;
        String err = stderr.toLowerCase();
        if (err.contains("no such file") || err.contains("找不到")) {
            return ExecErrorType.FILE_NOT_FOUND;
        }
        if (err.contains("not recognized") || err.contains("不是内部或外部命令")) {
            return ExecErrorType.COMMAND_NOT_FOUND;
        }
        if (err.contains("permission") || err.contains("拒绝访问")) {
            return ExecErrorType.PERMISSION_DENIED;
        }
        return ExecErrorType.RUNTIME_ERROR;
    }
//    private Charset getConsoleCharset() {
//        String os = System.getProperty("os.name").toLowerCase();
//        if (os.contains("win")) {
//            return Charset.forName("GBK");
//        }
//        return StandardCharsets.UTF_8;
//    }
}
