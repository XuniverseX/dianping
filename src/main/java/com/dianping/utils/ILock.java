package com.dianping.utils;

public interface ILock {
    boolean tryLock(long timeoutSec);

    void unlock();
}
