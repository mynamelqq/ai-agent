package com.ai.servevrpexls.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class ImageSearchTest {
    @Resource
    private ImageSearch imageSearch;
    @Test
    void searchImage() {
        List<String> computer = imageSearch.searchImage("computer");
        System.out.println(computer);
    }
}