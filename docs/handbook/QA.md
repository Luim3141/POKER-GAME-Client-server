# QA cẩm nang hệ thống và client portable — 11/10/2026

## Sản phẩm

- DOCX mới: `Hieu_toan_bo_he_thong_Poker_20261011.docx`; không ghi đè báo cáo học phần/mẫu trường.
- 34 trang trong Word và LibreOffice PDF; 18 Heading 1, 58 Heading 2, 12 hình, 11 bảng, 3 danh sách đánh số Word thật.
- TOC field cấp 1–2, hyperlinks/bookmarks; Word cập nhật và đối chiếu 76 đề mục với số trang thật qua Range.Information(3). Word PDF là tham chiếu pagination chính; LibreOffice có thể đẩy một vài heading/row sang trang khác mặc dù tổng trang bằng nhau. TOC của DOCX đúng theo Word.
- `render_docx.py` render toàn bộ 34 trang. Đã xem tất cả trang: 26 trang không đổi được xác nhận bằng SHA-256 so với vòng QA trước đã xem, tám trang thay đổi (3, 28–34) được xem lại. Bảng có header lặp, không cắt row; chữ/sơ đồ không chồng khối hoặc tràn lề. Có các trang minh họa lớn nên trang trước hình có khoảng trắng để không cắt sơ đồ.
- QA PDF/PNG: `target/handbook-qa-20261011-final/`; Word PDF: `target/handbook-qa-20261011-v3/Word_reference.pdf`. Chỉ dùng QA, không phải báo cáo nghiệm thu mạng.
- Font Calibri 11.5 body, Heading 1/2 đen 20/14 pt, code Consolas 9.5; Letter portrait, lề ngang 1 inch, lề dọc 0.75 inch. Sơ đồ PlantUML local, PNG dpi 180, có alt text/caption. Không dùng mẫu trường vì đây là cẩm nang, không phải báo cáo.

## Đối chiếu nội dung

| Mảng kiến thức | Source/chứng cứ chính |
| --- | --- |
| Dependency, build, portable | pom.xml, build-portable-client.ps1, launcher/package verification |
| Cấu hình, framing/envelope | AppConfig, ProtocolCodec, Message, MessageType |
| Route/RBAC, tài khoản | ServerController.authorize/handleAuthenticated, AuthenticationService, SessionManager |
| Thread/queue/writer | PokerServer, ClientHandler, PokerTableRuntime, PokerClient |
| UI/state/effects | MainController, ClientState, FXML/CSS, TableEffects, PlayingCardView, WagerRange, ClientWindow |
| Luật/chia bài/payout | PokerTableRuntime, BettingRound, PotManager, HandEvaluator, DealTiming, GlorySummary |
| Disconnect/resume | ConnectionRegistry, LobbyManager, PokerTableRuntime, PokerClient; audit 11/10 |
| Database/coin/admin | schema.sql, migration V2, JdbcHandRepository, JdbcAccountAdministration |
| Kiểm thử hiện có | AUDIT_RESULTS_20261011.md, review-qa-20261011 XML, package Surefire XML |
| Deployment | setup-radmin-firewall.ps1, run-server.cmd, AppConfig, resource client.properties |

Điểm được sửa trong quá trình đối chiếu: tên LobbyManager (không phải LobbyService), tên script firewall, Player không có SPECTATE_TABLE trong authorization hiện tại, enum không đồng nghĩa API đã xử lý, live hand không được persist liên tục ở đầu hand, phân biệt net wallet/refund/payout. Không sửa code game để phù hợp lời mô tả.

## Danh mục sơ đồ và nguồn

| File .puml/.png | Loại/view | Nguồn |
| --- | --- | --- |
| architecture | C4 Container, rút gọn | Entrypoints, PokerServer, UI, JDBC |
| network_classes | Class, rút gọn method/field | client.network, server.network/lobby/table |
| threads | Thread/queue | PokerServer, ClientHandler, runtime, PokerClient |
| hand | Activity vòng đời hand | runtime, phase/street, settlement |
| dealing | Sequence | beginDeal/scheduler/finishDeal, DealTiming |
| action | Sequence action | PokerClient.action, ServerController, runtime |
| login | Sequence login | MainController, AuthenticationService, SessionManager |
| disconnect | Activity, chính sách source 11/10 | disconnect/reconnect, settlement cleanup |
| coin_sequence | Sequence transaction/push | ServerController, JdbcAccountAdministration |
| erd_game | Physical ERD view gameplay | schema.sql |
| erd_accounts | Physical ERD view tài khoản/ledger | schema.sql; FK phụ ghi chú |
| deployment | UML deployment cấu hình dự kiến | VPN endpoint, bind, firewall, JDBC localhost |

Mười sơ đồ tái sử dụng nguồn đã đối chiếu từ `docs/reports/update-20261008/diagrams/`, render lại cục bộ; hai activity hand/disconnect tạo mới. Chữ ký class được rút gọn cho mục đích giải thích, không phải listing toàn bộ code. Không gửi source lên dịch vụ render công khai; không sử dụng sơ đồ resume cũ giữ seat ngoài hand.

## Portable verification

- ZIP build: `target/distribution/20261011-185833/PokerClient-Windows-x64-20261011-185833.zip`.
- Bản bàn giao cùng byte ở `releases/PokerClient-Windows-x64-20261011-185833.zip`, không bị Maven clean xóa.
- 66.858.678 byte; SHA256 `B5BE8BA7E7945C0953DC03D32A0F79383F56B6275E103A579449D1D07A8B6BEE`.
- CRC, runtime Java 21.0.10, current bytecode/resources, default endpoint, guide byte identity, client-only allowlist PASS. Không persistence/server launcher/MariaDB driver/SQL/script quản trị trong gói. jdeps không có missing dependency.
- Build bằng PowerShell 7 thành công, chạy lại 74 unit tests, 0 failure/error. Lần gọi ban đầu dưới Windows PowerShell 5 gặp NativeCommandError do Java stderr, không dùng kết quả đó làm release.
- EXE thật mở, Responding=True, rồi đóng đúng PID smoke 21732. Không có IPv4 26.* được gán lúc kiểm tra, nên không ghi nhận auto-connect/PONG VPN cho release này. Listener 9090 đang có process khác được giữ nguyên.
- Không chạy lại DB/TCP hoặc UiSmokeIT trong đợt packaging/documentation; số 12 integration và năm DPI trong cẩm nang là lần review trước cùng ngày, có XML riêng. Chưa nghiệm thu máy thứ hai/Windows sạch/load-soak/DB treo/mất điện.

## Ranh giới thao tác

Không sửa source game/schema, không mutation production DB/firewall/VPN, không tự thay hoặc restart server đang chạy, không xóa release cũ. Chỉ build, smoke launcher không login, kiểm tra gói, tạo cẩm nang và cập nhật README/PROGRESS. Cần deploy server source tương ứng ở ranh giới hand an toàn trước khi nhận các client mới.
