package com.hmdp.dto;

import lombok.Data;
/**
 * @description: 用户数据传输对象：把带有敏感信息的User对象转化成不带敏感信息的UserDto对象
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
