package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import edu.cmu.cs214.booking.domain.Booking;
class BookingServiceTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final Room roomB = new Room("B", "Beta", 4);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");

    private BookingService newService() {
        return new BookingService(new InMemoryBookingStore());
    }

    @Test
    void bookConfirmsWhenRoomIsFree() {
        BookingService svc = newService();
        BookingResult r = svc.book(roomA, alice, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void bookWaitlistsWhenSlotIsTaken() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomA, bob, new TimeInterval(630, 700));
        assertInstanceOf(BookingResult.Waitlisted.class, r);
    }

    @Test
    void backToBackBookingsAreConfirmed() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomA, bob, new TimeInterval(660, 720));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void sameSlotInDifferentRoomsAreBothConfirmed() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomB, bob, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void listBookingsReturnsConfirmedBookings() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        svc.book(roomA, bob, new TimeInterval(660, 720));
        assertEquals(2, svc.listBookings(roomA).size());
    }
    @Test
    void listBookingsOnlyReturnsBookingsForRequestedRoom() {
        BookingService svc = newService();

        // 往两个不同房间放入同一时段的预订（不同房间不冲突）
        svc.book(roomA, alice, new TimeInterval(600, 660));
        svc.book(roomB, bob, new TimeInterval(600, 660));

        // 断言：查询 roomA 的预订，应当只返回 1 个，且房间是 roomA
        List<Booking> roomABookings = svc.listBookings(roomA);
        assertEquals(1, roomABookings.size(), "应当只返回 roomA 的预订");
        assertEquals(roomA, roomABookings.get(0).room(), "返回的预订房间必须是 roomA");
    }

    @Test
    void s1CancelRemovesConfirmedBooking() {
        BookingService svc = newService();
        BookingResult r = svc.book(roomA, alice, new TimeInterval(600, 660));
        Booking booking = ((BookingResult.Confirmed) r).booking();

        svc.cancelBooking(booking.id());

        assertEquals(0, svc.listBookings(roomA).size(), "取消后该房间不应再有预订");
    }

    @Test
    void s2CancelWithUnknownIdDoesNothing() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));

        svc.cancelBooking("no-such-id");

        assertEquals(1, svc.listBookings(roomA).size(), "无效 id 不应影响已有预订");
    }

    @Test
    void s3CancelPromotesEarliestWaiter() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        BookingResult r1 = svc.book(roomA, alice, new TimeInterval(600, 660));
        String aliceId = ((BookingResult.Confirmed) r1).booking().id();
        BookingResult r2 = svc.book(roomA, bob, new TimeInterval(620, 700));
        assertInstanceOf(BookingResult.Waitlisted.class, r2);

        svc.cancelBooking(aliceId);

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(1, bookings.size(), "取消后应晋升 Bob 为正式预订");
        assertEquals(bob, bookings.get(0).user(), "晋升的应当是候补的 Bob");
        assertEquals(0, store.waitlistForRoom(roomA).size(), "晋升后候补名单应为空");
    }

    @Test
    void s4SkipsWaiterWhoseIntervalStillConflicts() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        User carol = new User("u3", "Carol");
        User dave = new User("u4", "Dave");
        // Carol 的预订与 Bob（最早的候补者）冲突，取消 Alice 后仍保留
        String aliceId = ((BookingResult.Confirmed)
                svc.book(roomA, alice, new TimeInterval(780, 870))).booking().id();
        svc.book(roomA, carol, new TimeInterval(700, 760));
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, bob, new TimeInterval(680, 740)));
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, dave, new TimeInterval(800, 860)));

        svc.cancelBooking(aliceId);

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(2, bookings.size(), "应保留 Carol 的预订并晋升 Dave");
        assertEquals(0, bookings.stream().filter(b -> b.user().equals(bob)).count(),
                "Bob 仍与 Carol 冲突，不应被晋升");
        assertEquals(1, bookings.stream().filter(b -> b.user().equals(dave)).count(),
                "应跳过 Bob，晋升下一个合适的 Dave");
        assertEquals(1, store.waitlistForRoom(roomA).size(), "Bob 应仍在候补名单上");
    }

    @Test
    void s5PromotesNoOneWhenNoWaiterFits() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        User carol = new User("u3", "Carol");
        String aliceId = ((BookingResult.Confirmed)
                svc.book(roomA, alice, new TimeInterval(600, 700))).booking().id();
        svc.book(roomA, carol, new TimeInterval(720, 800));
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, bob, new TimeInterval(650, 750)));

        svc.cancelBooking(aliceId);

        assertEquals(1, svc.listBookings(roomA).size(), "无人合适时应只剩 Carol 的预订");
        assertEquals(carol, svc.listBookings(roomA).get(0).user());
        assertEquals(1, store.waitlistForRoom(roomA).size(), "Bob 应仍在候补名单上");
    }

    @Test
    void s6PromotesAtMostOneWaiterPerCancellation() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        User dave = new User("u4", "Dave");
        String aliceId = ((BookingResult.Confirmed)
                svc.book(roomA, alice, new TimeInterval(600, 660))).booking().id();
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, bob, new TimeInterval(620, 680)));
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, dave, new TimeInterval(630, 690)));

        svc.cancelBooking(aliceId);

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(1, bookings.size(), "每次取消至多晋升一人");
        assertEquals(bob, bookings.get(0).user(), "应晋升最早的候补者 Bob");
        assertEquals(1, store.waitlistForRoom(roomA).size(), "Dave 应仍在候补名单上");
    }
}
