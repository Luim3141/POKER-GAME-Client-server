# Poker rules và UI audit — 30/09/2026

Phạm vi: No-Limit Texas Hold’em, chip ảo, cash-style; không thêm rake, tournament levels, rebuy trong hand hoặc luật casino ngoài yêu cầu. Đã đọc `POKER_UX_RESEARCH.md`, `POKER_PAYOUT_FLOW_RESEARCH.md` và đối chiếu `BettingRound`, `PotManager`, `PokerTableRuntime`, FXML và action controls hiện có. Đây là bổ sung, không phải chứng nhận game đã được nghiệm thu.

## Luật xác minh lại từ nguồn chính thức

- Heads-up: dealer là small blind, action trước pre-flop và sau post-flop. Với bàn nhiều người, pre-flop bắt đầu bên trái big blind; các street sau bắt đầu từ live player bên trái button. [PokerStars: heads-up](https://www.pokerstars.com/help/articles/poker-rules-master/), [Texas Hold’em](https://www.pokerstars.com/poker/games/texas-holdem/).
- Flop có 3 lá, turn và river thêm 1 lá; hoàn tất cược mới chuyển street. Showdown dùng bộ 5 lá tốt nhất; sau payout button dịch clockwise trước hand mới. [PokerStars: Texas Hold’em](https://www.pokerstars.com/poker/games/texas-holdem/).
- Minimum raise dựa trên full bet/raise lớn nhất của street; amount sau chữ raise là tổng bet. Nhiều short all-in có thể cộng dồn để reopen, xét riêng từng người; minimum raise vẫn dựa trên full increment cuối. No-limit không cap số raise. [Poker TDA 2026, rules 45, 49, 50 và addendum 49](https://www.pokertda.com/view-poker-tda-rules/).
- Mỗi side pot split độc lập; odd chip board game trao cho winner đầu tiên bên trái button. Khi all-in và mọi betting action đã xong, table các live hands. [Poker TDA 2026, rules 17, 21, 23](https://www.pokertda.com/view-poker-tda-rules/). Đây là house policy project chọn từ chuẩn tournament.
- Side pot chỉ dành cho người đóng góp đủ vào pot đó; folded hand không thắng. Phần bet không được call được hoàn trả, không phải chiến thắng một side pot độc lập. [PokerStars: side pots](https://www.pokerstars.com/help/articles/poker-rules-master/), [WSOP 2026 tournament rules, rule 108: uncalled amount returned](https://assets.wsopcdn.com/wsop/1a72ba28-781c-409d-a9c3-5ca13c4c5718.pdf). Áp dụng hoàn trả như policy kế toán của project.

## Điểm cần đối chiếu/sửa trong code

1. **Short opening all-in:** nhánh `BET` đang gán `lastFullRaise = currentBet` kể cả khi bet thấp hơn big blind. Vì vậy BB 100, opener all-in 40 làm full-raise baseline giảm còn 40. Giữ baseline minimum bet khi chưa có full opening bet; thêm test cùng hành vi `ALL_IN` để hai đường không tạo luật khác nhau.
2. **Uncalled excess:** `PotManager` hiện tạo layer ngay cả khi chỉ có một contributor. Payout có thể bảo toàn tổng chip nhưng trình bày sai thành “Side pot thắng”, và có thể tăng winner/statistics sai. Phân biệt refund và pot award; thử A đóng 100, B all-in 60: contested pot 120, A được hoàn 40. Thử thêm folded highest contributor để không xuất hiện pot có eligibility rỗng. Đây là đề nghị triển khai, không phải thuật toán được chép từ nguồn.
3. **Reopen regression:** dùng tình huống 100 → 125 → call 125 → 200. Người đã bet 100 có quyền raise; người đã call 125 chỉ facing thêm 75 nên chưa reopen. Code đã có `lastActedBet`; bảo vệ bằng test, tránh đơn giản hóa thành global flag.
4. **Hand continuation:** giữ `BETWEEN_HANDS` rõ ràng, payout + số dư sau settlement hiển thị trước khi reset board, rồi Ready cho hand tiếp theo. Policy reset Ready hiện có là yêu cầu project, không phải luật poker phổ quát. Kiểm tra busted stack, player đang rời bàn, disconnect và settlement failure trước khi cho start hand mới.
5. **Projection:** docs UX cũ có khuyến nghị observer giấu hole cards, mâu thuẫn plan đồ án. Requirement được giữ: Observer login thấy tất cả hole cards, không action; Player chỉ thấy bài mình và reveal hợp lệ. Gắn nhãn UI “Chế độ giám sát · thấy toàn bộ bài” để tránh nhầm với spectator poker thông thường. Không thay quyền vì tham khảo sản phẩm thương mại.

## Hướng UI nên áp dụng

PokerStars công bố các thành phần table gồm felt, card deck, player images, stack và bet amounts; bet slider hỗ trợ shortcut và bước theo blind. [Table appearance](https://www.pokerstars.com/help/articles/table-appearance-feature/), [Bet slider](https://www.pokerstars.com/help/articles/bet-slider-options/). Áp dụng các nguyên tắc này, không sao chép artwork/brand:

- Bàn felt xanh trầm, rail tối, bài trắng có rank/suit lớn, card back đồng bộ. Chip stack trang trí không thay thế số tiền đọc được. Hạn chế gold gradient, glow, badge và quảng cáo giả; ưu tiên vùng chơi hơn nội dung trang trí.
- Thứ bậc: board + main/side pots ở tâm; seat có username, stack, current-street bet và dealer/blind markers; turn ring + giây; own cards/action tray ở dưới. Panel history phụ nhỏ hơn table, không lấn bàn ở cửa sổ laptop.
- Giữ 5 board slots cố định. Folded cards tối đi; disconnected/all-in có chữ; winner có `+amount` ngắn và pot-to-seat animation. Animation dùng payout server và kết thúc trước hand mới, không tự tính wallet.
- Giữ tối đa 3 action chính: `Fold`, `Check / Call X`, `Bet X / Raise to Y`; All-in là shortcut riêng. Field luôn là tổng street contribution; ghi “Bạn thêm X” để tránh nhầm total với increment. Preset chỉ điền amount và phải clamp theo min/max server; khi không được raise vẫn cho short call.
- Login auto-connect nhưng có status rõ `Đang kết nối / Đã kết nối / Thử lại`; route theo AccountRole. Back/leave phải đợi server xác nhận và báo lỗi nếu thất bại. Loading, empty và disconnected không được trông như dữ liệu cũ còn hợp lệ.

W3C yêu cầu không truyền đạt thông tin chỉ bằng màu; indicators/controls quan trọng có non-text contrast tối thiểu 3:1; pointer target tối thiểu 24×24 CSS px với các ngoại lệ. [Use of Color](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html), [Non-text Contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html), [Target Size](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html). Với JavaFX, dùng làm mục tiêu usability: action hit-area ≥40px, focus keyboard thấy rõ, suit bằng ký hiệu + rank, connection/turn bằng icon + text, font không bị co quá nhỏ khi resize.

## Acceptance scenarios trọng tâm

- Heads-up và 3-player: blinds, pre-flop/post-flop turn order, dealer hand tiếp.
- Check/call/bet/raise/all-in: min/max, short call, short opening, full raise, cumulative reopen, wrong turn và stale version.
- Unequal all-ins, folded contribution, split/odd chip, uncalled refund; tổng final stacks bằng tổng starting stacks.
- Uncontested fold không reveal bài đối thủ; showdown/all-in reveal đúng; Observer vẫn thấy tất cả theo requirement.
- Payout được thấy một lần; next Ready không còn pot/animation cũ; busted player không làm bàn kẹt.
- Disconnect/resume ở từng street; back/leave/logout khi có command pending; settlement failure không hiện thắng giả.
- UI ở cửa sổ mặc định 1280×820, kích thước tối thiểu 1100×800 và cửa sổ lớn: bảng không chồng nút, board/pot không nhảy, controls có focus, khoản cược luôn đọc được. Đây là desktop layout; không cam kết hỗ trợ cửa sổ nhỏ hơn minimum của launcher.

## House rule cho người rời bàn và dead side pot

Project giữ policy rời bàn giữa hand là Fold. Nếu toàn bộ contributor còn quyền tranh một side pot đều rời/Fold, lớp dead money không có người eligible được gộp vào pot thấp hơn gần nhất còn live hand; những side pot có eligibility bình thường không đổi. Đây là house rule cho đường Leave riêng của ứng dụng, không được gán là luật PokerStars/TDA. Kiểm thử buộc tổng final stacks bằng tổng starting stacks và không cho pot eligibility rỗng làm kẹt showdown. Trước khi build pots, phần cược street cao nhất không được ai match được trả về người đã cược (kể cả đã Fold), ghi `uncalledReturns` riêng với payout: các khoản đã được call vẫn ở trong pot. Việc trả uncalled bet kể cả đã muck được xác nhận tại [Poker TDA 2026 rule 16-B](https://www.pokertda.com/view-poker-tda-rules/).
