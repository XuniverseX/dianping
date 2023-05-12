package com.dianping.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.dianping.dto.Result;
import com.dianping.entity.VoucherOrder;
import com.dianping.mapper.VoucherOrderMapper;
import com.dianping.service.ISeckillVoucherService;
import com.dianping.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianping.utils.RedisIdWorker;
import com.dianping.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    String queueName = "stream.orders";

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(() -> {
            while (true) {
                try {
                    // 获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BlOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    if (list == null || list.isEmpty()) {
                        // 消息队列中没有消息，循环
                        continue;
                    }
                    // 解析消息中的订单信息
                    MapRecord<String, Object, Object> records = list.get(0);
                    Map<Object, Object> values = records.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 创建订单
                    handleVoucherOrder(voucherOrder);
                    // 确认消息 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", records.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        });
    }

    private void handlePendingList() {
        while (true) {
            try {
                // 获取pendingList中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(queueName, ReadOffset.from("0"))
                );
                if (list == null || list.isEmpty()) {
                    // pendingList中没有消息，无异常，跳出
                    break;
                }
                // 解析消息中的订单信息
                MapRecord<String, Object, Object> records = list.get(0);
                Map<Object, Object> values = records.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                // 创建订单
                handleVoucherOrder(voucherOrder);
                // 确认消息 SACK stream.orders g1 id
                stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", records.getId());
            } catch (Exception e) {
                log.error("处理pending-list异常", e);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    IVoucherOrderService proxy;

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean isLock = lock.tryLock();
        // 判断锁是否获取成功
        if (!isLock) {
            log.error("{} 不允许重复下单", userId);
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 订单id
        long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        // 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        int res = result.intValue();
        if (res != 0) {
            // 没有购买资格
            return Result.fail(res == 1 ? "库存不足" : "不能重复下单");
        }
        // 返回订单id
        return Result.ok(orderId);
    }

/*
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int res = result.intValue();
        if (res != 0) {
            // 没有购买资格
            return Result.fail(res == 1 ? "库存不足" : "不能重复下单");
        }

        // 有购买资格，将订单信息放入阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 用户id
        voucherOrder.setUserId(userId);
        // 代金券id
        voucherOrder.setVoucherId(voucherId);
        // 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 放入阻塞队列
        orderTasks.add(voucherOrder);
        // 返回订单id
        return Result.ok(orderId);
    }
*/

/*
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 判断优惠券是否可用
        if (voucher.getBeginTime().compareTo(LocalDateTime.now()) > 0) {
            return Result.fail("秒杀未开始");
        }
        if (voucher.getEndTime().compareTo(LocalDateTime.now()) < 0) {
            return Result.fail("秒杀已结束");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
        // 创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean isLock = lock.tryLock();
        // 判断锁是否获取成功
        if (!isLock) {
            // 获取锁失败，返回错误或重试
            return Result.fail("不允许重复下单");
        }
        try {
            // 获取代理对象 (事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, userId);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }
*/

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 一人一单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("用户已经购买过一次");
            return;
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")    // set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            log.error("库存不足");
            return;
        }

        save(voucherOrder);
    }
}
