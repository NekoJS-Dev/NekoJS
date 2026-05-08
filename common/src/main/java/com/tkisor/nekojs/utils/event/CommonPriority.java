package com.tkisor.nekojs.utils.event;

/**
 * @author ZZZank
 */
public interface CommonPriority {

    byte HIGHEST = Byte.MAX_VALUE;

    byte HIGH = Byte.MAX_VALUE / 2;

    byte NORMAL = 0;

    byte LOW = Byte.MIN_VALUE / 2;

    byte LOWEST = Byte.MIN_VALUE;
}
