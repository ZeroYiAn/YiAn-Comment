package com.hmdp.utils;
/**
 * @description: 锁接口
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
public interface Ilock {
    /**
     * 尝试获取锁
     * @param timeoutSec  锁持有的超时时间，过期后自动释放
     * @return true 表示锁获取成功，false表示获取失败
     */
    boolean tryLock(Long timeoutSec);
    /**
     * 释放锁
     */
    void unlock();
}
