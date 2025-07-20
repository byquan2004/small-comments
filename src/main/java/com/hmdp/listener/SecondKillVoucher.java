package com.hmdp.listener;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.hmdp.utils.MQConstant.*;

@Component
@RequiredArgsConstructor
public class SecondKillVoucher {

    private static final Logger log = LoggerFactory.getLogger(SecondKillVoucher.class);

    private final IVoucherOrderService voucherOrderService;

    /**
     * 保存订单信息
     * @param order
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = SECOND_KILL_QUEUE),
            exchange = @Exchange(name = DEFAULT_DIRECT_EXC, type = "direct"),
            key = { ORDER_SECKILL_BIND_KEY }
    ))
    public void receive(VoucherOrder order) {
        log.info("收到id为 {} 的订单", order.getId());
        try {
            voucherOrderService.save(order);
        } catch (Exception e) {
            log.info("保存订单失败：{}",e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
