package com.demo.imagefetcher.service

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap


@Service
class FakeRedisService {

    private val locks: MutableMap<String?, Boolean?> = ConcurrentHashMap<String?, Boolean?>()

    fun tryLock(key: String?): Boolean {
        return locks.compute(key) { k: String?, v: Boolean? ->
            if (v == null || !v) {
                return@compute true // acquire lock
            }
            false // already locked

        }!!
    }

    fun unlock(key: String?) {
        locks.remove(key)
    }
}