package com.ai.aigent.constant;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL) // 空字段不序列化
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    // 状态码
    private int code;

    // 响应消息
    private String message;

    // 响应数据
    private T data;

    // 时间戳
    private LocalDateTime timestamp;

    // 请求路径（可选）
    private String path;

    // 追踪ID（用于日志追踪）
    private String traceId;

    // 分页信息
    private PageInfo page;

    /**
     * 私有构造方法
     */
    private Result() {
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 成功响应（有数据）
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCodeEnum.SUCCESS.getCode());
        result.setMessage(ResultCodeEnum.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> Result<T> success(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCodeEnum.SUCCESS.getCode());
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    /**
     * 失败响应（使用状态码枚举）
     */
    public static <T> Result<T> error(ResultCodeEnum resultCode) {
        return error(resultCode.getCode(), resultCode.getMessage());
    }

    /**
     * 失败响应（自定义状态码和消息）
     */
    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /**
     * 失败响应（使用状态码枚举，带数据）
     */
    public static <T> Result<T> error(ResultCodeEnum resultCode, T data) {
        Result<T> result = new Result<>();
        result.setCode(resultCode.getCode());
        result.setMessage(resultCode.getMessage());
        result.setData(data);
        return result;
    }

    /**
     * 失败响应（自定义消息，使用默认错误码）
     */
    public static <T> Result<T> error(String message) {
        return error(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode(), message);
    }

    /**
     * 设置路径
     */
    public Result<T> path(String path) {
        this.path = path;
        return this;
    }

    /**
     * 设置追踪ID
     */
    public Result<T> traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    /**
     * 设置分页信息
     */
    public Result<T> page(PageInfo page) {
        this.page = page;
        return this;
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return ResultCodeEnum.isSuccess(this.code);
    }

    /**
     * 分页信息类
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PageInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        // 当前页
        private Integer pageNum;

        // 每页数量
        private Integer pageSize;

        // 总记录数
        private Long total;

        // 总页数
        private Integer pages;

        // 是否有上一页
        private Boolean hasPrevious;

        // 是否有下一页
        private Boolean hasNext;

        /**
         * 创建分页信息
         */
        public static PageInfo of(Integer pageNum, Integer pageSize, Long total) {
            PageInfo pageInfo = new PageInfo();
            pageInfo.setPageNum(pageNum);
            pageInfo.setPageSize(pageSize);
            pageInfo.setTotal(total);

            if (total != null && pageSize != null && pageSize > 0) {
                int pages = (int) Math.ceil((double) total / pageSize);
                pageInfo.setPages(pages);
                pageInfo.setHasPrevious(pageNum != null && pageNum > 1);
                pageInfo.setHasNext(pageNum != null && pages > 0 && pageNum < pages);
            }

            return pageInfo;
        }

        /**
         * 从MyBatis Plus的Page创建
         */
        public static <T> PageInfo fromMyBatisPlus(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
            return of(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal()
            );
        }


    }
}