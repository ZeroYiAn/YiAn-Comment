package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * @description: Redis数据
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
