# Lượt cược, mức cược và hiệu ứng bàn

## Quy tắc đang áp dụng

- Texas Hold’em chuẩn, tối đa 8 ghế. D/SB/BB cố định trong một hand; chỉ xoay khi bắt đầu hand kế tiếp.
- Preflop: người còn quyền hành động sau BB mở lượt. Flop/turn/river: người còn quyền hành động sau D mở lượt. Bỏ qua ghế trống, Fold, all-in; disconnected vẫn chịu timeout Check/Fold.
- Heads-up: D đồng thời là SB, mở preflop; BB mở postflop. Người cuối preflop có thể là người đầu flop, không phải lỗi. Hand bắt đầu nhiều người không đổi sang luật heads-up chỉ vì có người Fold.
- Khi chuyển từ nhiều người sang heads-up ở hand mới, điều chỉnh D nếu cần để người vừa đóng BB không đóng BB hai hand liên tiếp.
- Raise/re-raise khiến những người cần theo hành động lại theo chiều kim đồng hồ; D/BB không luôn hành động cuối sau raise.

Đối chiếu: [Poker TDA](https://www.pokertda.com/view-poker-tda-rules/) và [PokerStars Texas Hold’em](https://www.pokerstars.com/poker/learn/lesson/texas-holdem-rules/).

## Chọn mức cược

- Kéo thanh hoặc nhập số nguyên chính xác. Thanh có 1.001 vị trí; ô nhập không bị giới hạn bởi độ phân giải thanh.
- Thanh đặt ngay trên ô nhập, cùng nhóm tối đa 220 logical px; không giãn ngang toàn action tray.
- Action tray dùng padding/nút gọn; trạng thái và thông tin lượt đặt cạnh nhau. Nhóm cược ở bên phải chỉ hiện khi đến lượt của mình, không hiện trong DEALING hoặc khi chờ người khác; pot và nhật ký vẫn hiển thị.
- Bet là mức mở cược; Raise to là **tổng cược trong vòng**, không phải số chip trả thêm.
- Giới hạn từ ACTION_REQUIRED: minimumRaiseTo đến streetContribution + stack. Short all-in chỉ có một mức thì khóa thanh/ô nhập tại mức tối đa.
- Nhập dở/sai giữ nguyên nội dung, báo lỗi bên dưới; không tự sửa số của người chơi.
- Chỉ nút Bet/Raise gửi action. Kéo thanh không gửi TCP. Không phải lượt, pending action, disconnected hoặc Observer không có quyền cược.
- Prompt mới cập nhật giới hạn và mức gợi ý; server vẫn kiểm tra quyền, hand và stateVersion.
- Bet/Raise vượt tổng chip còn có thể cược bị server từ chối trước khi đổi state, không âm thầm chuyển thành all-in.

## Bài và hiệu ứng

- 52 lá vẽ cục bộ bằng JavaFX Canvas, không tải bộ ảnh và không truyền file ảnh.
- Giữ node ghế/bài giữa snapshot. Chia bài hai vòng theo chiều kim đồng hồ sau D; bài chung mới xuất hiện từng lá. Player chỉ thấy bài được server cho phép, Observer nhận projection riêng.
- Server điều phối DEALING → BETTING: 350 ms bay mỗi lá, 450 ms giữa các lần phát và 200 ms dự phòng. Timer 20 giây chỉ bắt đầu khi DEALING kết thúc. Flop ba lá riêng; Turn/River một lá; all-in runout cũng đi từng street.
- Trong DEALING: không có currentTurn, không ACTION_REQUIRED; cược sớm bị NOT_READY. Scheduler chỉ enqueue callback, kiểm tra hand/generation trước mutation. Reconnect dùng remainingMillis và elapsed bằng System.nanoTime, không phát lại cả chuỗi hoặc chờ client ACK. Client nhận muộn hội tụ về BETTING ngay.
- Sau settlement thành công: thông báo chúc mừng, payout từng pot, hoàn cược tách riêng; chip bay về ghế. Rồng Canvas dài khoảng 45% viewport bay nhiều vùng trong 5 giây, lửa quanh mọi ghế nhận pot; bảng bài/ghế được bảo vệ khỏi lớp rồng. Khung/avatar vàng và THẮNG +amount giữ đến hand tiếp theo, kể cả resume qua winnerPayouts.
- Kết quả vẫn đọc được khi tắt hiệu ứng. Ready không chờ trang trí. Lỗi settlement không có HAND_RESULT/chúc mừng; kết quả trùng không phát lại.
- Menu Hiển thị: Đầy đủ / Giảm chuyển động / Tắt; lưu Preferences. Hai chế độ sau giữ kết quả/tier nhưng bỏ chuyển động trang trí.
- Resize tính lại tọa độ bài/chip/khung người thắng. Rời trang/bàn, đăng xuất, mất kết nối, hand mới hủy trang trí cũ. Thu nhỏ dừng trang trí; mở lại hiển thị state hiện tại, không phát lại hàng đợi; heartbeat vẫn chạy.

## Quan sát hành động và chip cược

- TABLE_ACTION do server phát sau mutation hợp lệ, trước chuyển vòng/payout: sequence theo hand, userId từ seat, street/action, paid, streetContribution và automatic. Hai blinds và timeout/leave Fold cũng được ghi nhận; không chứa bài riêng.
- Snapshot giữ 16 action gần nhất. Mỗi ghế có badge action cuối của street hiện tại; nhật ký hiển thị sáu action, giữ hành động cuối vòng khi chuyển street. Raise to là tổng trong vòng; +amount là phần trả thêm.
- paid > 0 tạo cụm đồng xu từ avatar vào pot trong 550 ms. Check/Fold/reject không có đồng xu; sequence trùng bị bỏ qua. Resume snapshot khôi phục nhãn/lịch sử nhưng không replay xu cũ.
- Animation không thay đổi wallet, pot, stateVersion hoặc timer. Full/Reduced/Off chỉ ảnh hưởng trang trí; dấu hiệu thắng và text vẫn đọc được.

## Hai bậc độc lập

| Tài sản | Wallet tối thiểu |
|---|---:|
| Đồng | 0 |
| Bạc | 25.000 |
| Vàng | 100.000 |
| Bạch kim | 500.000 |
| Kim cương | 2.000.000 |

| Thành tích | Số ván tối thiểu | Tỷ lệ nhận pot tối thiểu |
|---|---:|---:|
| Tân binh | Chưa đủ 20 | — |
| Đồng | 20 | 0% |
| Bạc | 50 | 20% |
| Vàng | 100 | 25% |
| Kim cương | 300 | 30% |
| Huyền thoại | 1.000 | 35% |

Chọn bậc cao nhất đạt cả hai điều kiện. Bậc có thể giảm. Wins là định nghĩa hiện tại của DB: nhận ít nhất một pot (kể cả hòa); losses = gamesPlayed − wins. Không phải Elo và không bảo đảm lãi ròng. Admin cộng coin chỉ ảnh hưởng tài sản; không tăng wins/gamesPlayed. Không có thưởng coin/quyền gameplay theo bậc.

GlorySummary do server tạo từ users.chips/wins/games_played khi login/join và sau commit. Mỗi seat giữ thống kê công khai trong bộ nhớ, không query DB mỗi action/snapshot. Dữ liệu thiếu hiển thị “Chưa có thống kê”. Tooltip hồ sơ/ghế/ranking nêu thắng/thua và điều kiện bậc tiếp; viền ghế theo thành tích, badge theo tài sản.

## Chạy và tương thích

- Source mới: `run-server.cmd` và `run-ui.cmd`, sau khi MariaDB/Radmin đã chạy theo hướng dẫn hiện có.
- Không migration. Snapshot có phase, dealDurationMillis/dealRemainingMillis/dealCardCount, recentActions và winnerPayouts, bên cạnh blind seats/glory. Cần server/client cùng phiên bản: client portable cũ có thể từ chối trường mới hoặc TABLE_ACTION.
- Chưa tạo ZIP/app-image mới. Gói 20261005-130213 giữ nguyên; chỉ đóng gói khi người dùng yêu cầu.
- UI fixtures kiểm tra trình bày qua TCP cùng máy, không chứng minh kết nối Radmin từ máy thứ hai. DB tests chỉ dùng `poker_test`, không sửa schema/data `poker_game`.

## Lệnh kiểm tra không đóng gói portable

```powershell
.\mvnw.cmd test
$env:POKER_DB_TEST_URL='jdbc:mariadb://127.0.0.1:3306/poker_test'
.\mvnw.cmd -Pdb-it test-compile failsafe:integration-test failsafe:verify
.\mvnw.cmd -Pui-it test-compile failsafe:integration-test failsafe:verify '-DargLine=-Dglass.win.uiScale=1.5' '-Dpoker.ui.screenshotDir=target/ui-smoke/features-final-150'
```

Thay output scale bằng 1.0/1.25/1.75/2.0 để kiểm tra DPI khác. Glass override chỉ dùng test, không thay Display Settings/launcher.
