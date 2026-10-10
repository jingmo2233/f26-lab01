package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import org.junit.jupiter.api.Test;

class IsAvailableTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final User alice = new User("u1", "Alice");

    @Test
    void notAvailableWhenAnEarlierBookingRunsIntoTheRequest() {
        BookingService svc = new BookingService(new InMemoryBookingStore());

        // 1. 已有预订 [540, 720) —— 完全覆盖了待查询的时间段
        svc.book(roomA, alice, new TimeInterval(540, 720));

        // 2. 查询 [600, 660)，它明明完全在已有预订内部，应该是不可用！
        assertFalse(svc.isAvailable(roomA, new TimeInterval(600, 660)),
                "已有预订 [540,720) 包含了查询区间，应该返回 false（不可用）");
    }
}