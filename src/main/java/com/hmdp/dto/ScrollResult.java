package com.hmdp.dto;

import lombok.Data;

import java.util.List;
/**
 * @description: 数据传输对象
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
