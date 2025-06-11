//package com.bacpham.kanban_service.configuration.redis;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.HashOperations;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.stereotype.Service;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.concurrent.TimeUnit;
//
//@Service
//public class GenericRedisService<K, F, V> {
//
//    private final RedisTemplate<K, V> redisTemplate;
//    private final HashOperations<K, F, V> hashOperations;
//
//    @Autowired
//    public GenericRedisService(RedisTemplate<K, V> redisTemplate) {
//        this.redisTemplate = redisTemplate;
//        this.hashOperations = redisTemplate.opsForHash();
//    }
//
//    public void set(K key, V value) {
//        redisTemplate.opsForValue().set(key, value);
//    }
//
//    public void setTimeToLive(K key, long timeout, TimeUnit timeUnit) {
//        redisTemplate.expire(key, timeout, timeUnit);
//    }
//
//    public void hashSet(K key, F field, V value) {
//        hashOperations.put(key, field, value);
//    }
//
//    public boolean hashExists(K key, F field) {
//        return hashOperations.hasKey(key, field);
//    }
//
//    public V get(K key) {
//        return redisTemplate.opsForValue().get(key);
//    }
//
//    public Map<F, V> getField(K key) {
//        return hashOperations.entries(key);
//    }
//
//    public V hashGet(K key, F field) {
//        return hashOperations.get(key, field);
//    }
//
//    public List<V> hashGetByFieldPrefix(K key, String fieldPrefix) {
//        List<V> result = new ArrayList<>();
//        Map<F, V> entries = hashOperations.entries(key);
//        for (Map.Entry<F, V> entry : entries.entrySet()) {
//            if (entry.getKey().toString().startsWith(fieldPrefix)) {
//                result.add(entry.getValue());
//            }
//        }
//        return result;
//    }
//
//    public Set<F> getFieldPrefixes(K key) {
//        return hashOperations.entries(key).keySet();
//    }
//
//    public void delete(K key) {
//        redisTemplate.delete(key);
//    }
//
//    public void delete(K key, F field) {
//        hashOperations.delete(key, field);
//    }
//
//    public void delete(K key, List<F> fields) {
//        for (F field : fields) {
//            hashOperations.delete(key, field);
//        }
//    }
//}
