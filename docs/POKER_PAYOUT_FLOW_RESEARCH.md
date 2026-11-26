# Texas Hold'em cash-game payout flow

Nghiên cứu ngày 2026-09-22. Phạm vi: luật payout/showdown cho bàn Texas Hold'em cash-style của project. Nguồn là tài liệu first-party của PokerStars và quy tắc chính thức Poker TDA. Poker TDA là chuẩn tournament, nên dùng các quy tắc chung về showdown, side pot và odd chip như một **house policy được chọn rõ ràng**, không suy diễn các quy định tournament khác vào cash game.

## Quy tắc cần giữ ở server

### 1. Kết thúc hand

- Nếu chỉ còn một player có live hand sau fold, award toàn bộ pot mà không cần reveal/showdown. Người fold mất quyền với chips đã đưa vào pot. Nếu từ hai live hand trở lên còn lại sau river, chạy showdown trên best five-card hand từ hai hole cards và năm board cards.
- Một betting round chỉ được đóng khi các player còn live đã fold hoặc đã match final bet; sau đó mới deal street tiếp theo. Khi hand hoàn tất, button dịch một seat theo chiều kim đồng hồ trước hand kế tiếp.
- Cash game có blinds cố định; player có thể sit/leave theo house policy. `Ready` không phải poker action: chỉ allow ở trạng thái giữa hands, và server phải định nghĩa rõ auto-ready hay reset-ready.

Nguồn: [PokerStars — Texas Hold'em Rules](https://www.pokerstars.com/poker/learn/lesson/texas-holdem-rules/) (betting-round completion, showdown, button movement, cash-game characteristics).

### 2. Main pot, side pot, và eligibility

- Chỉ player đóng góp vào side pot mới eligible để thắng side pot đó. All-in player không được thắng phần bets vượt quá total contribution của họ. Nhiều all-in có thể tạo nhiều side pots.
- Tính pot theo **từng ngưỡng contribution duy nhất**: với threshold `t`, phần pot là `(t - previousThreshold) * số player có contribution >= t`; eligible winners là player live có contribution >= `t`. Award từng layer độc lập; một player có thể thắng main pot trong khi player khác thắng side pot.
- Đừng dùng một `winner` cho cả hand. Persist/emit một danh sách immutable `PotAward { kind/main-or-side, amount, eligiblePlayerIds, winnerIds, shareByWinner }`, rồi credit stacks đúng một lần theo danh sách đó.

Nguồn: [PokerStars — Poker rules](https://www.pokerstars.com/help/articles/poker-rules-master/) (side-pot creation/eligibility); [PokerStars — Hand rankings](https://www.pokerstars.com/poker/learn/lesson/poker-hand-rankings/) (main/side pots có thể có winner khác nhau); [Poker TDA Rule 23](https://www.pokertda.com/view-poker-tda-rules/) (mỗi side pot split riêng).

### 3. Split pot và odd chips

- Nếu các player eligible có identical best five-card hand (bao gồm kickers), chia **pot layer đó** đều giữa các winner.
- Chọn house policy xác định, deterministic cho remainder. Policy phù hợp board game theo Poker TDA: break odd chips xuống denomination nhỏ nhất đang dùng; odd chip còn lại đi đến winner đầu tiên bên trái button. Áp dụng lại cho từng main/side pot, sau khi đã xác định winners của pot đó.
- Với chip unit là 1 (play-money integer), `share = amount / winnerCount`, `remainder = amount % winnerCount`; duyệt winners theo vòng từ seat left of button để award mỗi odd chip. Không dùng thứ tự map/hash hay username.

Nguồn: [PokerStars — Poker rules](https://www.pokerstars.com/help/articles/poker-rules-master/) (split pot chia đều); [Poker TDA Rule 21](https://www.pokertda.com/view-poker-tda-rules/) (odd-chip policy); [Poker TDA Rule 23](https://www.pokertda.com/view-poker-tda-rules/) (side pots split riêng).

### 4. Showdown và card visibility

- Online Hold'em thường auto-show all hands tại showdown. Đối với order nếu project muốn trình bày tuần tự: last aggressor ở final betting round show first; nếu không có final-street bet, first active player left of button show first. Winning hands phải được show.
- Nếu player all-in và betting action đã xong, reveal/table tất cả hands trong main và side pots; nếu hand kết thúc do all other players fold, không bắt buộc reveal winning cards.
- Server snapshot quyết định card visibility. Client không tự reveal hidden hole cards; result snapshot chỉ chứa cards được phép công khai và reason (`SHOWDOWN`, `UNCONTESTED_FOLD`, `ALL_IN_RUNOUT`).

Nguồn: [PokerStars — Poker rules](https://www.pokerstars.com/help/articles/poker-rules-master/) (show-first order/winning hands); [PokerStars — Texas Hold'em Rules](https://www.pokerstars.com/poker/learn/lesson/texas-holdem-rules/) (online auto-show); [Poker TDA Rules 17–18](https://www.pokertda.com/view-poker-tda-rules/) (all-in table/reveal và showdown order).

## UI payout contract tối thiểu

- Trong hand: luôn hiển thị `Main pot N`; khi tồn tại thì thêm `Side pot 1 N`, `Side pot 2 N` thay vì một total mơ hồ. Tại seat hiển thị street bet, `All-in` và stack để người chơi kiểm tra amount đang facing.
- Result state phải tồn tại trước khi board/pot reset: board cuối, các cards đã reveal hợp lệ, hand category nếu evaluator có, và payout per pot (`Alice wins Main pot +120`; `Bob wins Side pot 1 +40`; `Main pot split: Alice +31, Bob +30`). Đây là hệ quả trực tiếp của side-pot eligibility và independently split pot, không phải chỉ animation.
- Luôn ghi chính xác amount/bet ở UI; Poker TDA nhấn mạnh player cần thấy/có thể estimate stack và clear bet amount, còn all-in indicator giảm nhầm lẫn về undercall. PokerStars mô tả online tables và auto-showdown, nên result feedback nên rõ ràng và server-authoritative, không dựa vào suy đoán của client.
- Khi `HAND_RESULT` đã acknowledge/render xong, chuyển `WAITING/READY` theo policy project; chỉ sau đó reset street bets, board và pot rồi deal next hand. Việc này giữ payout audit được và tránh UI ghép payout cũ với hand mới.

Nguồn: [Poker TDA Rules 51, 62 và RP-1](https://www.pokertda.com/view-poker-tda-rules/) (correct amount, countable stacks, all-in indicator); [PokerStars — Texas Hold'em Rules](https://www.pokerstars.com/poker/learn/lesson/texas-holdem-rules/) (online showdown, button/next hand).

## Acceptance checks cho implementation

- [ ] Uncontested fold awards all contributions once; no showdown cards leaked.
- [ ] Three players with unequal all-ins produce correct main/side-pot amounts and each pot only considers eligible live hands.
- [ ] A tied main pot and a different side-pot winner pay independently.
- [ ] An odd one-chip remainder uses the button-relative, deterministic policy per pot.
- [ ] Result payload has a payout entry for every pot and remains renderable until the table transitions to next-hand/waiting state.
- [ ] Sum of all `PotAward.amount` equals total contributions, and sum of all credits equals that same amount (before any explicitly supported rake).

## Decision boundary

Không thêm rake, run-it-twice, insurance/cash-out, muck choice, timer, hoặc cash-game sit-out/blind-entry policy vào payout engine nếu project chưa yêu cầu. Những feature này thay đổi payout/state contract và cần requirement riêng.
