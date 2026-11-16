package com.ai.aigent.tools;

import org.junit.jupiter.api.Test;

class DownloadResourceToolTest {

    @Test
    void downloadResource() {
        DownloadResourceTool dr = new DownloadResourceTool();
        String url ="https://pics7.baidu.com/feed/a9d3fd1f4134970aca7b721192c08cd8a7865d9a.jpeg@f_auto?token=0b1ca310a1c82294fb1e9cd03c258e2a";
        String fileName = "1350662.png";
        String result = dr.downloadResource(url,fileName);
        System.out.println(result);
    }
}