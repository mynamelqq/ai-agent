package com.ai.aigent.entity.VO;

import lombok.Data;

import java.util.List;

@Data
public class BochaWebSearchResponse {
    private Integer code;
    private String log_id;
    private String msg;
    private Datas data;

    @Data
    public static class Datas {
        private WebPages webPages;
    }


    @Data
    public static class WebPages {
        private Integer totalEstimatedMatches;
        private List<WebPageValue> value;
    }

    @Data
    public static class WebPageValue {
        private String name;
        private String url;
        private String snippet;
        private String summary;
        private String siteName;
        private String datePublished;
    }
}