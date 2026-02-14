package demo;

import org.jspecify.annotations.Nullable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 测试用例：更复杂的空指针场景。
 */
public class OrderProcessor {

    private final Map<String, Order> orders = new HashMap<>();

    @Nullable
    public Order findOrder(String orderId) {
        return orders.get(orderId);
    }

    // ========== 场景 7: 嵌套对象的 null ==========

    public BigDecimal getOrderTotal(String orderId) {
        // ⚠️ findOrder 返回 @Nullable
        Order order = findOrder(orderId);
        return order.getTotal();
    }

    public String getOrderCustomerName(String orderId) {
        Order order = findOrder(orderId);
        // ⚠️ order 可能为 null，order.getCustomer() 也可能为 null
        Customer customer = order.getCustomer();
        return customer.getName();
    }

    // ========== 场景 8: 方法链 ==========

    public String getFormattedTotal(String orderId) {
        // ⚠️ 整条链都可能 NPE
        return findOrder(orderId).getTotal().toString();
    }

    // ========== 内部类 ==========

    public static class Order {
        private BigDecimal total;
        private @Nullable Customer customer;

        public BigDecimal getTotal() { return total; }

        @Nullable
        public Customer getCustomer() { return customer; }
    }

    public static class Customer {
        private String name;

        public String getName() { return name; }
    }
}
