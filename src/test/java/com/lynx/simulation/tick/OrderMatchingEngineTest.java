package com.lynx.simulation.tick;

import com.lynx.simulation.events.SimulatedClock;
import com.lynx.simulation.kafka.producers.OrderUpdateProducer;
import com.lynx.simulation.model.*;
import com.lynx.simulation.repository.OrderRepository;
import com.lynx.simulation.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderMatchingEngineTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TradeRepository tradeRepository;
    @Mock private OrderUpdateProducer orderUpdateProducer;
    @Mock private SimulatedClock simulatedClock;
    @InjectMocks private OrderMatchingEngine engine;

    private static final Instant NOW = Instant.parse("2024-01-15T10:00:00Z");

    @BeforeEach
    void setUp() {
        when(simulatedClock.getSimulatedInstant()).thenReturn(NOW);
        engine.setFeeRate(0.001);
    }

    private Stock stock(double price) {
        return Stock.builder().ticker("ARKA").currentPrice(price).build();
    }

    private Order order(String type, Order.Side side, int qty, Double limitPrice) {
        return Order.builder()
                .orderId("ord-1")
                .orderType(type).side(side)
                .quantity(qty).filledQuantity(0)
                .limitPrice(limitPrice)
                .status(OrderStatus.PENDING)
                .platformId("plat-1").platformUserId("user-1")
                .instrumentType("STOCK").instrumentId("ARKA")
                .build();
    }

    private void givenOrders(Order... orders) {
        when(orderRepository.findByInstrumentIdAndStatusIn(eq("ARKA"), any()))
                .thenReturn(List.of(orders));
    }

    // --- fill triggering ---

    @Test
    void matchOrders_marketOrder_alwaysFills() {
        Order o = order("MARKET", Order.Side.BUY, 10, null);
        givenOrders(o);

        engine.matchOrders(stock(100.0));

        assertThat(o.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(o.getFilledQuantity()).isEqualTo(10);
    }

    @Test
    void matchOrders_limitBuy_currentAtOrBelowLimit_fills() {
        Order o = order("LIMIT", Order.Side.BUY, 10, 130.0);
        givenOrders(o);

        engine.matchOrders(stock(128.0)); // 128 <= 130 → fill

        assertThat(o.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void matchOrders_limitBuy_currentAboveLimit_doesNotFill() {
        Order o = order("LIMIT", Order.Side.BUY, 10, 130.0);
        givenOrders(o);

        engine.matchOrders(stock(132.0)); // 132 > 130 → no fill

        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void matchOrders_limitSell_currentAtOrAboveLimit_fills() {
        Order o = order("LIMIT", Order.Side.SELL, 10, 120.0);
        givenOrders(o);

        engine.matchOrders(stock(122.0)); // 122 >= 120 → fill

        assertThat(o.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void matchOrders_limitSell_currentBelowLimit_doesNotFill() {
        Order o = order("LIMIT", Order.Side.SELL, 10, 120.0);
        givenOrders(o);

        engine.matchOrders(stock(118.0)); // 118 < 120 → no fill

        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void matchOrders_expiredOrder_setsExpiredStatus() {
        Order o = order("LIMIT", Order.Side.BUY, 10, 200.0);
        o.setExpiresAt(Instant.parse("2024-01-15T09:00:00Z")); // before NOW
        givenOrders(o);

        engine.matchOrders(stock(100.0));

        assertThat(o.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    void matchOrders_notExpiredOrder_isNotExpired() {
        Order o = order("LIMIT", Order.Side.BUY, 10, 50.0); // limit won't trigger at 100
        o.setExpiresAt(Instant.parse("2024-01-15T17:00:00Z")); // after NOW
        givenOrders(o);

        engine.matchOrders(stock(100.0));

        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // --- fill calculations ---

    @Test
    void fill_limitOrder_executesAtLimitPriceNotCurrentPrice() {
        Order o = order("LIMIT", Order.Side.BUY, 10, 130.0);
        givenOrders(o);

        engine.matchOrders(stock(125.0)); // current below limit → fills at 130

        assertThat(o.getAverageFillPrice()).isEqualTo(130.0);
    }

    @Test
    void fill_marketOrder_executesAtCurrentPrice() {
        Order o = order("MARKET", Order.Side.BUY, 10, null);
        givenOrders(o);

        engine.matchOrders(stock(100.0));

        assertThat(o.getAverageFillPrice()).isEqualTo(100.0);
    }

    @Test
    void fill_calculatesExchangeFeeCorrectly() {
        // fee = price * qty * rate = 100 * 10 * 0.001 = 1.0
        Order o = order("MARKET", Order.Side.BUY, 10, null);
        givenOrders(o);

        engine.matchOrders(stock(100.0));

        assertThat(o.getExchangeFee()).isEqualTo(1.0);
    }

    @Test
    void fill_createsTradeRecord() {
        Order o = order("MARKET", Order.Side.BUY, 10, null);
        givenOrders(o);

        engine.matchOrders(stock(100.0));

        verify(tradeRepository).save(any(Trade.class));
    }

    @Test
    void fill_usesSimulatedTimeForUpdatedAt() {
        Order o = order("MARKET", Order.Side.BUY, 10, null);
        givenOrders(o);

        engine.matchOrders(stock(100.0));

        assertThat(o.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void fill_sendsOrderUpdateOverKafka() {
        Order o = order("MARKET", Order.Side.BUY, 10, null);
        givenOrders(o);

        engine.matchOrders(stock(100.0));

        verify(orderUpdateProducer).sendOrderUpdate(o);
    }

    // --- expireOpenOrders ---

    @Test
    void expireOpenOrders_limitOrders_setToExpired() {
        Order limit = order("LIMIT", Order.Side.BUY, 10, 130.0);
        when(orderRepository.findByStatusIn(any())).thenReturn(List.of(limit));

        engine.expireOpenOrders();

        assertThat(limit.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    void expireOpenOrders_marketOrders_setToRejected() {
        Order market = order("MARKET", Order.Side.BUY, 10, null);
        when(orderRepository.findByStatusIn(any())).thenReturn(List.of(market));

        engine.expireOpenOrders();

        assertThat(market.getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void expireOpenOrders_usesSimulatedTimeForUpdatedAt() {
        Order o = order("LIMIT", Order.Side.BUY, 10, 130.0);
        when(orderRepository.findByStatusIn(any())).thenReturn(List.of(o));

        engine.expireOpenOrders();

        assertThat(o.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void expireOpenOrders_sendsUpdateForEveryOrder() {
        Order limit = order("LIMIT", Order.Side.BUY, 5, 100.0);
        Order market = order("MARKET", Order.Side.SELL, 5, null);
        when(orderRepository.findByStatusIn(any())).thenReturn(List.of(limit, market));

        engine.expireOpenOrders();

        verify(orderUpdateProducer, times(2)).sendOrderUpdate(any());
    }

    @Test
    void expireOpenOrders_emptyOrderBook_doesNothing() {
        when(orderRepository.findByStatusIn(any())).thenReturn(List.of());

        engine.expireOpenOrders();

        verify(orderRepository, never()).saveAll(any());
        verify(orderUpdateProducer, never()).sendOrderUpdate(any());
    }

    @Test
    void setFeeRate_appliedToSubsequentFills() {
        engine.setFeeRate(0.01); // 1%
        Order o = order("MARKET", Order.Side.BUY, 10, null);
        givenOrders(o);

        engine.matchOrders(stock(100.0));

        assertThat(o.getExchangeFee()).isEqualTo(10.0); // 100 * 10 * 0.01
    }

    // --- trade record field validation ---

    @Test
    void fill_capturesAllTradeFieldsCorrectly() {
        Order o = order("MARKET", Order.Side.BUY, 10, null);
        givenOrders(o);

        engine.matchOrders(stock(100.0));

        ArgumentCaptor<Trade> captor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).save(captor.capture());
        Trade saved = captor.getValue();

        assertThat(saved.getTradeId()).isNotNull();
        assertThat(saved.getOrderId()).isEqualTo("ord-1");
        assertThat(saved.getPlatformId()).isEqualTo("plat-1");
        assertThat(saved.getPlatformUserId()).isEqualTo("user-1");
        assertThat(saved.getInstrumentType()).isEqualTo("STOCK");
        assertThat(saved.getInstrumentId()).isEqualTo("ARKA");
        assertThat(saved.getSide()).isEqualTo(Order.Side.BUY);
        assertThat(saved.getQuantity()).isEqualTo(10);
        assertThat(saved.getPrice()).isEqualTo(100.0);
        assertThat(saved.getExchangeFee()).isEqualTo(1.0);
        assertThat(saved.getExecutedAt()).isEqualTo(NOW);
    }

    // --- expiry boundary ---

    @Test
    void matchOrders_expiresAt_exactlyNow_doesNotExpire() {
        // isAfter() is strict — expiresAt == now means the order is still live
        Order o = order("LIMIT", Order.Side.BUY, 10, 50.0); // won't fill at 100
        o.setExpiresAt(NOW);
        givenOrders(o);

        engine.matchOrders(stock(100.0));

        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // --- partial fill completion ---

    @Test
    void fill_previouslyPartiallyFilledOrder_completesWithWeightedAveragePrice() {
        // Order had 4 of 10 shares filled at 110.0 previously
        Order o = order("MARKET", Order.Side.BUY, 10, null);
        o.setFilledQuantity(4);
        o.setAverageFillPrice(110.0);
        o.setExchangeFee(0.44); // 110 * 4 * 0.001
        o.setStatus(OrderStatus.PARTIALLY_FILLED);
        givenOrders(o);

        engine.matchOrders(stock(100.0)); // fills remaining 6 at 100.0

        // weighted avg = (110*4 + 100*6) / 10 = 104.0
        assertThat(o.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(o.getFilledQuantity()).isEqualTo(10);
        assertThat(o.getAverageFillPrice()).isEqualTo(104.0);
        assertThat(o.getExchangeFee()).isEqualTo(1.04); // 0.44 + (100*6*0.001)
    }

    @Test
    void fill_feeRateChangedAfterPartialFill_completionUsesNewRate() {
        // Completion chunk is priced at the rate active at the time of fill, not the original
        Order o = order("MARKET", Order.Side.BUY, 10, null);
        o.setFilledQuantity(4);
        o.setAverageFillPrice(110.0);
        o.setExchangeFee(0.44); // accumulated from prior chunk at 0.001
        o.setStatus(OrderStatus.PARTIALLY_FILLED);
        givenOrders(o);

        engine.setFeeRate(0.01); // rate changes before the completion tick
        engine.matchOrders(stock(100.0));

        // completion chunk: 100 * 6 * 0.01 = 6.0; total = 0.44 + 6.0 = 6.44
        assertThat(o.getExchangeFee()).isEqualTo(6.44);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.FILLED);
    }
}
