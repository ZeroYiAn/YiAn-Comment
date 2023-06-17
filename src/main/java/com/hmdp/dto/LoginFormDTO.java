package com.hmdp.dto;

import lombok.Data;
/**
 * @description: 数据传输对象
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
