package com.ai.servevrpexls;

import com.ai.servevrpexls.tools.ImageSearch;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ServevrPexlsApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServevrPexlsApplication.class, args);
	}

	@Bean
	public ToolCallbackProvider toolCallbackProvider(ImageSearch imageSearch) {
		return MethodToolCallbackProvider.builder().toolObjects(imageSearch).build();
	}
}
