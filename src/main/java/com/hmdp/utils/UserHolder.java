package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
/**
 * @description: 用户对象工具类，把UserDTO对象存到ThreadLocal中进行线程隔离
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
