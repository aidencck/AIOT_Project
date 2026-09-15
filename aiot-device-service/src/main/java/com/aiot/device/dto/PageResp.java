package com.aiot.device.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@Schema(name = "PageResp", description = "统一分页响应")
public class PageResp<T> {

    @Schema(description = "总记录数", example = "100")
    private Long total;

    @Schema(description = "当前页码，从 1 开始", example = "1")
    private Integer pageNo;

    @Schema(description = "每页条数", example = "20")
    private Integer pageSize;

    @Schema(description = "当前页记录")
    private List<T> records;

    public static <T> PageResp<T> from(IPage<T> page) {
        PageResp<T> resp = new PageResp<>();
        resp.setTotal(page == null ? 0L : page.getTotal());
        resp.setPageNo(page == null ? 1 : Long.valueOf(page.getCurrent()).intValue());
        resp.setPageSize(page == null ? 0 : Long.valueOf(page.getSize()).intValue());
        resp.setRecords(page == null || page.getRecords() == null ? Collections.emptyList() : page.getRecords());
        return resp;
    }
}
