package com.ai.servevrpexls.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ImageSearch {
    private static final String api_key = "PvI3BazHSidShIlpvGUWQ6qHhRaoMRnuawRDMS07Z60KBCLZQslfjOL2";
    @Tool(description = "search image from web")
    public List<String> searchImage(@ToolParam(description = "search query keyword") String query) {
         // 构建请求URL
        String url = String.format("https://api.pexels.com/v1/search?query=%s&per_page=%d",
                                  query, 3);

            // 发送GET请求
            String result = HttpRequest.get(url)
                    .header(Header.AUTHORIZATION, api_key)
                    .header(Header.CONTENT_TYPE, "application/json")
                    .timeout(5000) // 设置超时时间5秒
                    .execute()
                    .body();

            JSONObject response= JSONUtil.parseObj(result);
            return JSONUtil.parseObj(response)
            .getJSONArray("photos")
            .stream()
            .map(photoObj -> (JSONObject) photoObj)
            .map(photoObj -> photoObj.getJSONObject( "src"))
            .map(photo -> photo.getStr( "medium"))
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toList());




    }
}
