# No-Limit Texas Hold'em: flow và UI contract

Nghiên cứu ngày 2026-09-22. Phạm vi là bàn **No-Limit Texas Hold'em play-money/cash-style** của project. Poker TDA là nguồn chuẩn cho semantics cược; các quy định riêng của tournament không tự động áp dụng cho project.

## 1. Luồng người chơi hoàn chỉnh

```text
Login / Register
  -> Home / Lobby
  -> Create table | Join seat | Observe table
  -> Waiting room: chọn ghế -> Ready / Unready
  -> Hand start: button -> small blind -> big blind -> 2 hole cards
  -> Pre-flop betting
  -> Flop (3 community cards) -> betting
  -> Turn (1 card) -> betting
  -> River (1 card) -> betting
  -> Showdown hoặc kết thúc sớm khi chỉ còn 1 người chưa fold
  -> chia main pot / side pots -> hand result
  -> button dịch theo chiều kim đồng hồ -> next hand / waiting
```

- Button xác định vị trí; người bên trái button post small blind, người kế tiếp post big blind. Pre-flop bắt đầu bên trái big blind; post-flop bắt đầu từ người còn bài đầu tiên bên trái button.
- Heads-up là ngoại lệ quan trọng: button post small blind và action trước pre-flop, big blind action trước post-flop. Không dùng thứ tự bàn 3+ người cho bàn 2 người.
- Mỗi người nhận 2 hole cards riêng. Board lần lượt có flop 3 lá, turn 1 lá, river 1 lá; giữa các lần chia là một betting round.
- Một betting round chỉ kết thúc khi mọi người còn bài đã fold, all-in, hoặc đóng góp bằng mức cược hiện tại. Nếu chỉ còn một người chưa fold, họ thắng ngay và không cần showdown.
- Nếu từ hai người trở lên còn bài sau river, showdown chọn bộ 5 lá mạnh nhất từ 7 lá khả dụng; hòa thì chia pot. Main pot và từng side pot phải được xử lý độc lập.
- Sau khi award pot, hiển thị winner, hand category, số chip thắng và card reveal hợp lệ trong một khoảng ngắn; sau đó mới reset board/pot và bắt đầu hand kế tiếp.

Nguồn: [PokerStars — Texas Hold'em rules](https://www.pokerstars.com/poker/learn/lesson/texas-holdem-rules/), [PokerStars — Texas Hold'em game rules](https://www.pokerstars.com/poker/games/texas-holdem/), [PokerStars — poker rules/help, gồm heads-up](https://www.pokerstars.com/help/articles/poker-rules-master/), [Poker TDA 2026 — rules 17, 18, 23](https://www.pokertda.com/view-poker-tda-rules/).

### `Ready` là state của ứng dụng, không phải poker action

- Chỉ cho `Ready / Unready` khi người dùng đã ngồi ghế và không có hand đang chạy.
- Start hand khi đủ số player mà project yêu cầu và tất cả player hợp lệ đã ready.
- Khi hand đang chạy, khóa đổi ghế/ready. Observer không có ready.
- Sau hand, hoặc giữ ready để auto-start hand sau, hoặc reset ready cho tất cả; phải chọn **một** policy và thể hiện rõ bằng label (`Auto-ready: On`) để không có trạng thái ngầm.

## 2. Action contract và semantics cược

| Tình huống | Action hợp lệ trên UI |
|---|---|
| Chưa có bet ở street hiện tại | `Check`, `Bet`, `All-in` |
| Đang facing bet | `Fold`, `Call X`, `Raise to Y`, `All-in` |
| Stack còn lại <= call amount | `Fold`, `Call all-in X` (không phải raise) |
| Player đã fold hoặc all-in | Không có action |
| Không phải lượt / observer / waiting | Action tray disabled hoặc ẩn |

Quy tắc cần encode ở server và phản chiếu ở UI:

- `Check` chỉ hợp lệ khi `toCall = 0`.
- `Call` trả `min(toCall, stack)`; short call dùng toàn bộ stack và tạo all-in.
- Opening bet tối thiểu là big blind, trừ trường hợp all-in với stack nhỏ hơn.
- No-limit cho phép bet/raise tối đa toàn bộ stack.
- **Input raise phải là tổng mức cược sau action (`Raise to`)**, không phải phần tăng thêm. UI phải hiện đồng thời `To call X`, `Min raise to Y`, `Stack Z` để tránh mơ hồ.
- Minimum raise increment bằng **largest prior full bet/raise increment** trong street. Ví dụ bet 20 thì min raise to 40; bet 20 rồi raise to 60 (increment 40) thì min re-raise to 100.
- Short all-in nhỏ hơn một full raise vẫn hợp lệ nhưng không tự động reopen quyền raise cho người đã action. Nhiều short all-in chỉ reopen khi tổng amount người đó phải đối mặt đạt ít nhất một full raise; min raise vẫn dựa trên full legal increment cuối.
- Không cap số lần raise trong no-limit. Side pots phải được tính khi player all-in khác stack.

Nguồn: [PokerStars — action và minimum raise examples](https://www.pokerstars.com/poker/learn/lesson/texas-holdem-rules/), [Poker TDA 2026 — rules 45 và 49](https://www.pokertda.com/view-poker-tda-rules/).

### Bet controls đề xuất

- Primary buttons tối đa 3 nút theo state: `Fold`, `Check/Call X`, `Bet/Raise to Y`; thêm `All-in` như shortcut rõ ràng.
- Numeric field/slider luôn clamp `[minRaiseTo, maxRaiseTo]`; preset vừa đủ: `1/2 Pot`, `3/4 Pot`, `Pot`, `All-in`. Không tạo action mới cho preset—chúng chỉ điền amount.
- Khi server trả snapshot mới, amount hợp lệ phải được tính lại; không gửi action dựa trên state version cũ.
- Double-submit phải bị khóa sau click cho đến khi `ACTION_ACCEPTED`, `ACTION_REJECTED` hoặc snapshot mới tới.
- Hiện turn countdown gần player đang action và trong action tray. Timeout policy cần rõ: nếu facing bet thì fold; nếu không facing bet thì check (PokerStars cũng dùng check-or-fold tùy tình huống).

Nguồn UI control: [PokerStars — bet slider và shortcuts](https://www.pokerstars.com/help/articles/bet-slider-options/), [PokerStars — decision time/time bank](https://www.pokerstars.com/help/articles/trn-time-bank/).

## 3. Information hierarchy của table page

### Luôn thấy, không phụ thuộc role

1. **Table header:** back/leave, table name/id, `NLHE`, blinds `SB / BB`, số ghế, connection status.
2. **Game status:** `Waiting`, `Pre-flop`, `Flop`, `Turn`, `River`, `Showdown`; hand number nếu có.
3. **Center table:** 5 board slots có vị trí cố định, `Main pot`, từng `Side pot`, dealer/status message.
4. **Mỗi seat:** username, stack, bet của street hiện tại, trạng thái (`Ready`, `Folded`, `All-in`, `Sitting out`, `Disconnected`), marker `D / SB / BB`, highlight turn và countdown.
5. **Event area:** action gần nhất, winner/result, lỗi server có thể xử lý; chat/history đặt panel phụ, không cạnh tranh với action tray.

### Player-only

- 2 hole cards của chính mình, đủ rank và suit; đối thủ dùng card back cho tới lúc reveal hợp lệ.
- Action tray chỉ sáng đúng lượt, có `to call`, min/max và số chip còn lại sau action.
- Card component cần hiển thị bằng **rank + suit symbol**, không chỉ bằng hình/màu; red/black chuẩn và có thể thêm four-colour deck. Giữ kích thước/slot ổn định để board không nhảy layout.

### Observer

- Thấy board, pot, stack công khai, bets, status, turn và kết quả.
- Không thấy hole cards riêng trong hand đang chạy; chỉ thấy cards được reveal tại showdown/all-in theo rule của bàn.
- Không có `Ready`, `Take action`, bet slider hoặc seat-only controls. Dùng `Take seat` nếu còn chỗ và server cho phép; `Back to lobby` phải gửi stop-spectate.
- Chat của observer, nếu có, phải tách khỏi gameplay và tuân policy; PokerStars hạn chế observer chat và cấm nội dung ảnh hưởng hand đang chạy.

Nguồn: [PokerStars — table appearance fields/card options/messages](https://www.pokerstars.com/help/articles/table-appearance-feature/), [PokerStars — observer/chat restrictions](https://www.pokerstars.com/help/articles/chat-feature-instructions/44737/), [PokerStars — chat must not affect play](https://www.pokerstars.com/help/articles/rule-22-warning/10677/).

## 4. Page split và navigation

Chỉ cần 3 page chính; dialog là overlay, không phải page mới:

1. **Auth page**
   - Tabs `Login` / `Register`, server status, validation/error inline.
   - Login thành công -> Home. Logout/xóa session -> Auth.

2. **Home / Lobby page**
   - Header: user, balance/stats nếu có, refresh, logout.
   - Table list/cards: table name, blinds, seats `n/max`, status, primary action `Join` hoặc `Watch`.
   - `Create table` là dialog (name, max players, blinds), không tách page.
   - History/invites là panel/tab phụ. Empty/loading/error states phải riêng biệt.

3. **Table page**
   - Cùng một scene cho waiting, playing, showdown; render theo state machine, không đổi page giữa các street.
   - `Back to lobby` luôn có ở header. Observer: stop spectate rồi back. Player chưa vào hand: leave seat/table rồi back. Player đang trong hand: confirm rằng leave sẽ fold/sit out theo server policy; không được chỉ chuyển UI trong khi server vẫn giữ ghế.
   - Dialog xác nhận chỉ dùng cho hành động có hậu quả (`Leave during hand`, `All-in` nếu product muốn); không chặn `Fold/Check/Call` bằng dialog.

Luồng lobby tham chiếu: PokerStars cho phép lọc/chọn table, mở table để xem, chọn ghế/buy-in hoặc auto-seat; full table chuyển sang waiting list. Project có thể giữ `Create / Join / Observe` hiện tại thay vì sao chép toàn bộ waiting-list system. Nguồn: [PokerStars — join Play Money tables](https://www.pokerstars.com/help/articles/pkr-feat-joining-pm-games/211807/).

## 5. State invariants để tránh xung đột UI/network

- Server snapshot là source of truth; UI không tự suy ra winner, pot hoặc legal actions từ animation.
- Mỗi page chỉ có một active subscription/listener; rời table phải hủy table listener trước khi bind lobby listener.
- Dùng `stateVersion`: bỏ snapshot cũ hơn version đang render; action gửi kèm version nếu protocol cho phép.
- `viewerRole` quyết định visibility/enablement; không suy role từ việc `holeCards` rỗng.
- Action tray chỉ enable khi: role player, seated, hand active, `currentTurnUserId == me`, status active, connected và chưa có request pending.
- Hole cards là dữ liệu nhạy cảm: snapshot observer tuyệt đối không chứa private cards; ẩn ở client là chưa đủ.
- Hand result phải được render trước reset; snapshot next hand không được ghép board/pot của hand trước.
- Reconnect/resume phải đưa user vào đúng page từ authoritative session/table state, không mặc định về lobby.
- Một route/back action chỉ phát một command (`LEAVE_TABLE` hoặc `STOP_SPECTATE`); chờ ACK/snapshot rồi mới hoàn tất navigation, và phải có error state/retry.

## 6. Acceptance checklist cho UI hoàn chỉnh

- [ ] Auth, Lobby, Table là ba page tách biệt; mọi page có navigation hợp lệ.
- [ ] Table render đủ 52 card faces qua một card component chung; own/revealed/back states đúng.
- [ ] Mọi street/action/turn/amount/pot/side-pot/all-in/showdown đều có state hiển thị.
- [ ] Button action chỉ xuất hiện khi hợp lệ; labels có exact amount (`Call 120`, `Raise to 300`).
- [ ] Observer không thể thấy private hole cards hoặc phát player action.
- [ ] Back/leave không để lại seat/table subscription mồ côi và không xung đột với hand đang chạy.
- [ ] Loading, empty, disconnected, reconnecting, rejected-action và timeout states được thiết kế, không chỉ happy path.
- [ ] Keyboard focus, text contrast, rank+suit labels và disabled state nhìn phân biệt được.

## Nguồn chính

- [PokerStars Learn — Texas Hold'em Rules](https://www.pokerstars.com/poker/learn/lesson/texas-holdem-rules/)
- [PokerStars — Texas Hold'em](https://www.pokerstars.com/poker/games/texas-holdem/)
- [PokerStars Help — Poker rules, gồm heads-up](https://www.pokerstars.com/help/articles/poker-rules-master/)
- [Poker TDA — 2026 Rules, Procedures and Illustration Addendum](https://www.pokertda.com/view-poker-tda-rules/)
- [PokerStars Help — Join Play Money Poker games](https://www.pokerstars.com/help/articles/pkr-feat-joining-pm-games/211807/)
- [PokerStars Help — Table appearance](https://www.pokerstars.com/help/articles/table-appearance-feature/)
- [PokerStars Help — Bet slider](https://www.pokerstars.com/help/articles/bet-slider-options/)
- [PokerStars Help — Decision time and time bank](https://www.pokerstars.com/help/articles/trn-time-bank/)
