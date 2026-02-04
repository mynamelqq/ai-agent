package com.ai.aigent.constant;

import lombok.Data;

@Data
public class PageRequest {
    public int pageNum;
    public int pageSize;
    public String sortBy;
    public String order;
    public String search;
}
