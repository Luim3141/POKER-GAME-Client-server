# Poker Network Game

Đồ án No Limit Texas Hold'em desktop bằng Java 21, JavaFX, TCP và MariaDB. Server kiểm tra lượt, áp dụng luật và quyết toán chip. Client gửi thao tác và hiển thị trạng thái theo quyền tài khoản.

Đọc [cẩm nang hệ thống](docs/handbook/Hieu_toan_bo_he_thong_Poker.md) để xem kiến trúc, giao thức và luồng nghiệp vụ. Phần [kết nối Radmin](#chơi-qua-radmin-vpn) bên dưới hướng dẫn chạy từ source và tạo ZIP portable.

## Cập nhật source 11/10/2026 — trạng thái bàn và mất kết nối

Thanh dưới bàn được thu gọn; nhóm nhập/kéo tiền chỉ hiện khi đến lượt cược. Disconnect ngoài hand giải phóng ghế ngay; trong hand giữ quyền resume và timeout hiện có, rồi xóa ghế offline sau settlement. Socket bị thay thế không được tiếp tục Ready/Leave/action; mỗi receiver chỉ đọc connection của chính nó.

Kết quả và giới hạn kiểm chứng: [AUDIT_RESULTS_20261011.md](docs/AUDIT_RESULTS_20261011.md). ZIP mới ngày 11/10 đã chứa các sửa đổi client này; ZIP 08/10 vẫn được giữ nhưng chưa có chúng. Chạy server/client cùng source mới bằng các script hiện có; chỉ thay server sau khi các hand đang chạy kết thúc.

## Cập nhật source 05/10/2026 — gameplay và UI

Đã bổ sung slider + nhập số đồng bộ, vị trí D/SB/BB cố định trong hand, xử lý chuyển sang heads-up, chia từng lá, chúc mừng/rồng/lửa ngắn và hai bậc vinh quang độc lập. Chi tiết: [GAMEPLAY_UX.md](docs/GAMEPLAY_UX.md).

Build source mới rồi chạy bằng `run-server.cmd` và `run-ui.cmd`. **Server/client phải cùng bản** vì snapshot và event có trường mới. ZIP portable mới nhất ngày 11/10/2026 gồm các cập nhật gameplay/UI và sửa lỗi client hiện tại.

## Chuẩn bị

1. Mở XAMPP và start MySQL. XAMPP hiện tại dùng MariaDB 10.4.32 tại port 3306.
2. Cài mới: import [database/schema.sql](database/schema.sql). Nâng cấp database cũ: backup `poker_game`, rồi import [database/migrations/V2__account_roles_and_coin_requests.sql](database/migrations/V2__account_roles_and_coin_requests.sql).
3. Nếu không dùng tài khoản `root` local mặc định, đặt biến môi trường:

```powershell
$env:POKER_DB_URL='jdbc:mariadb://127.0.0.1:3306/poker_game'
$env:POKER_DB_USER='poker_app'
$env:POKER_DB_PASSWORD='your-password'
```

Không public server ra Internet khi chưa thêm TLS. Password và session token không được ghi log, nhưng protocol TCP demo không mã hóa đường truyền.

## Build và chạy

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
.\run-server.cmd
.\run-cli.cmd
.\run-ui.cmd
```

Database integration test dùng schema riêng `poker_test`:

```powershell
$env:POKER_DB_TEST_URL='jdbc:mariadb://127.0.0.1:3306/poker_test'
.\mvnw.cmd -Pdb-it verify
```

Wrapper tự tải Maven 3.9.9 vào `.mvn/` ở lần chạy đầu. Server bind `0.0.0.0:19090`; client tự kết nối khi mở, mặc định dùng `127.0.0.1:19090` (hoặc nhập IP Radmin VPN trên giao diện). Có thể sửa host/port ở Login; đăng nhập/đăng ký sẽ kết nối lại nếu server chưa sẵn sàng.

## Admin và quản lý coin

Tạo Admin trực tiếp trên máy server (password được nhập ẩn, không lưu trong script):

```powershell
.\run-admin-setup.cmd
```

- `PLAYER`: tạo/join bàn, chơi, xem history và gửi một yêu cầu coin đang chờ.
- `OBSERVER`: chỉ xem lobby và spectate; không có wallet/action.
- `ADMIN`: dashboard realtime, approve/reject coin, lock/unlock, đổi Player/Observer, reset password và điều chỉnh coin có audit.
- Approval chỉ thực hiện khi Player không còn seat; ledger và balance được commit trong một transaction và không thể approve hai lần.

## Chơi qua Radmin VPN

Trên máy server, tạo/join Radmin network, sau đó mở PowerShell **Run as Administrator**:

```powershell
.\setup-radmin-firewall.ps1
.\run-server.cmd
.\check-radmin-network.ps1
```

Máy người chơi join cùng Radmin network, sau đó kiểm tra:

```powershell
$serverIp = Read-Host 'Nhập địa chỉ IPv4 Radmin của máy chủ'
ping $serverIp
Test-NetConnection $serverIp -Port 19090
```

Firewall chỉ cho inbound TCP 19090 trên adapter `Radmin VPN` từ `26.0.0.0/8`. MariaDB giữ ở localhost và không mở port 3306. Đây là mạng lab qua Radmin VPN, không port-forward Internet công khai và protocol ứng dụng chưa có TLS riêng.

Tạo gói portable kèm Java runtime:

```powershell
.\build-portable-client.ps1
```

Script dùng JDK 21 và ghi ZIP vào `target/distribution/<timestamp>/`. Người nhận giải nén ZIP, mở `PokerClient\PokerClient.exe`, rồi nhập IP Radmin và cổng của máy chủ ở màn hình đăng nhập. Gói mang theo Java runtime, JavaFX và Jackson.

Bàn hỗ trợ 2–8 người chơi, mặc định 8 ghế khi tạo bằng UI. Observer không chiếm ghế; người thứ 9 không thể join bàn đầy.

CLI hỗ trợ:

```text
register/login <username> <password>
lobby
create <smallBlind> <2..8>
join/spectate <tableId>
ready
fold/check/call/allin
bet/raise <targetAmount>
leave
quit
```

## Kiến trúc ngắn

- TCP frame: `[int32 length][UTF-8 JSON]`, tối đa 1 MiB.
- Mỗi socket có một read loop trong bounded thread pool.
- Tối đa 64 reader; dư connection được đóng ngay. Mỗi connection có outbound FIFO 128 frame và một virtual writer; slow reader không chặn table/heartbeat.
- Mỗi bàn có một command queue và một game thread; ClientHandler không sửa game state.
- Timer chỉ enqueue timeout command. Timeout check nếu không cần call, ngược lại fold.
- Hole cards được tạo thành snapshot riêng theo role, không lọc JSON sau khi serialize.
- Settlement lock wallet rows, ghi ledger, cập nhật chip/stats và đánh dấu hand settled trong cùng transaction.
- Unique key `(hand_id, user_id, transaction_type)` ngăn payout lặp.

## Demo kiểm thử thủ công

1. Chạy server, 3 CLI/UI player và 1 observer.
2. Register/login, refresh lobby, tạo bàn, join và ready.
3. Kiểm tra player chỉ thấy bài mình; observer thấy toàn bộ hole cards.
4. Chơi qua pre-flop, flop, turn, river và showdown.
5. Thử invalid action, all-in, side pot, disconnect rồi login/resume lại.
6. Refresh lobby để kiểm tra ranking; kiểm tra `hands`, `hand_actions`, `hand_players`, `wallet_transactions` trong phpMyAdmin.

## Kích thước cửa sổ client (04/10/2026)

- Menu **Hiển thị** luôn có ở góc trên: cửa sổ nhỏ 960×640, vừa 1280×800, lớn 1600×900, phóng to hoặc toàn màn hình.
- Có thể kéo cạnh/góc cửa sổ để đặt kích thước khác; không còn giới hạn tối thiểu 1100×800 hay bắt buộc mở phóng to.
- **F11** bật/tắt toàn màn hình; **Esc** thoát toàn màn hình. Phóng to vẫn giữ thanh tác vụ Windows.
- Thanh tiêu đề Windows có ba nút **Thu nhỏ / Phóng to–Khôi phục / Đóng** cạnh nhau ở góc trên bên phải, áp dụng cho Player, Observer và Admin. Thu nhỏ vẫn giữ kết nối, không tạm dừng ván đấu hoặc timer. Toàn màn hình ẩn thanh tiêu đề; nhấn Esc hoặc F11 để hiện lại.
- Preset dùng kích thước cửa sổ theo đơn vị logical của JavaFX và được giới hạn theo vùng làm việc của màn hình hiện tại, không nhân DPI thêm lần nữa. Giao diện co đồng đều khi thiếu diện tích để giữ bài, pot và controls trong cửa sổ; cửa sổ rất nhỏ sẽ có chữ/nút nhỏ tương ứng, nên dùng preset lớn hoặc phóng to để đọc rõ.
- Chạy source bằng `run-ui.cmd` hoặc tạo bản portable bằng script ở trên. Khi cập nhật gameplay hoặc giao thức, dùng cùng phiên bản cho server và client.

## Bản source gameplay/UI mới

Source hiện tại có pha chia bài do server điều phối trước timer cược, badge và nhật ký hành động, chip từ avatar vào pot và hiệu ứng rồng khi thắng. Xem [luồng gameplay/UI](docs/GAMEPLAY_UX.md).

## Giới hạn có chủ ý

- In-progress hand ở memory; server crash trước settlement sẽ không trừ chip.
- Không có TLS riêng, chỉ phù hợp localhost hoặc mạng lab Radmin VPN tin cậy.
- UI JavaFX chia page Login/Home/Table/History/Wallet/Admin, route theo AccountRole; game engine và network không phụ thuộc UI.

## Audit và kiểm thử UI (30/09/2026)

Đọc [kết quả audit](docs/AUDIT_RESULTS_20260930.md) và [đối chiếu luật từ nguồn chính thức](docs/POKER_RULES_UI_AUDIT_20260930.md).

```powershell
.\mvnw.cmd clean test
$env:POKER_DB_TEST_URL='jdbc:mariadb://127.0.0.1:3306/poker_test'
.\mvnw.cmd -Pdb-it verify
.\mvnw.cmd -Pui-it verify
```

Chạy hai profile riêng; `ui-it` cần Windows desktop/graphics, tạo ảnh tại `target/ui-smoke`. Các ảnh trong báo cáo là UI thật với fixture kiểm thử, không phải người dùng thật.

Khi cập nhật bản này, restart server và gửi **client ZIP mới cho toàn bộ người thử**: action nay kèm hand ID để chặn thao tác từ ván/bàn cũ. Không trộn client cũ với server mới. Không cần migration database mới cho đợt audit này.
