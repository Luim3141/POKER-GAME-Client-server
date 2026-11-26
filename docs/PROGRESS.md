# Tiến độ đồ án Poker Client–Server

Cập nhật gần nhất: **2026-10-26**

Quy ước: ✅ hoàn thành và đã kiểm thử; 🟡 đã triển khai nhưng còn acceptance test thủ công.

## Báo cáo bài tập lớn với CSDL ở phần đầu — 26/10/2026

- Tạo `docs/reports/Bao_cao_Bai_tap_lon_Poker_Nhom_1_20261026.docx`, 66 trang. Giữ bìa khung/logo, phân công, toàn bộ phần thân/phụ lục của bản 12/10 và thêm chương CSDL đầu tiên: 9 bảng, 71 cột, 15 FK, dictionary/constraint/index, transaction/coin/session/history/migration. Không ghi đè bản cũ.
- Vẽ lại ERD thành bốn view SVG/PNG cục bộ, đủ mọi FK/cột, có self-check khối không giao nhau và đường không xuyên khối. Hai ERD cũ trong phần hiện thực cũng thay bằng hình mới. Tổng báo cáo 18 hình/26 caption bảng; giữ đủ 554 đoạn và 12 bảng thân bài cũ trong kiểm tra đối chiếu.
- Word cập nhật TOC/danh mục và kiểm tra 89 dẫn trang; Word/canonical PDF cùng 66 trang. Xem đủ các trang của v4, đối chiếu hash và xem năm trang thay đổi của v5. Sửa cột NULL/type, caption SEQ, AUTO_INCREMENT và nghĩa action_type quản trị. Structural audit/self-check PASS. QA/source/evidence ở `docs/reports/btl-20261026/`, render cuối `target/report-btl-20261026/render-v5/`.
- Không sửa code game, DB, cấu hình hoặc đóng gói. Test trong báo cáo vẫn là chứng cứ 11/10, không gọi là chạy lại ngày 26/10; chưa nghiệm thu từ xa mới.

## Slide riêng Mạc Đăng Hiệp — 19/10/2026

- Tạo PPTX riêng tại `docs/presentation/hiep/Thuyet_trinh_Mac_Dang_Hiep_Game_Database_20261019_Hoan_chinh.pptx`: 13 slide trình bày + 3 slide code dự phòng, có lời nói và đường dẫn source tuyệt đối trong Speaker Notes. Nội dung thuộc phân vai Game/Database: trạng thái chip, pha hand, Raise, evaluator, side pot/payout, schema, JDBC settlement, coin, lịch sử/ranking và kiểm thử.
- Source code trích trực tiếp theo số dòng. Ví dụ side pot/payout ghi rõ minh họa; ảnh UI ghi rõ fixture. Kết quả 74 unit/12 integration là XML ngày 11/10, đã đối chiếu lại, không chạy lại game.
- Render và xem đủ 16 slide, sửa trích đoạn rollback/ledger cuối thành đúng dòng 37/85. Package/layout/font/native-table/import validator PASS; 16 Speaker Notes có nội dung, nguồn trích có self-check. QA/source builder tại `target/hiep-slides-20261019/`, receipt `validation-v2.json`. Chưa kiểm tra trình chiếu trực tiếp bằng PowerPoint hoặc máy chiếu lớp học. Không thay source game/cấu hình/DB/release.

## Bổ sung code để trình chiếu và bảo vệ — 19/10/2026

- Tạo bản mới `docs/presentation/Kich_ban_thuyet_trinh_Poker_Nhom_1_20261019_Co_code.docx`, giữ nguyên bản trước. 25 trang, phân vai bốn thành viên; thêm 16 trích đoạn / 164 dòng source thật, đường dẫn thư mục/file, phương thức, số dòng, lời nói theo code và chỉ dẫn mở sâu khi giảng viên hỏi. 14 câu hỏi kỹ thuật có vị trí code tương ứng.
- Word cập nhật TOC và kiểm tra bảy mục; render đủ 25 trang, xem toàn bộ v2 và bốn trang thay đổi của v3 (21–24; các PNG còn lại có hash không đổi). Self-check đối chiếu 164 dòng DOCX với source hiện tại PASS, không trang trống. QA tại `target/presentation-code-20261019-qa-v3/`.
- Không sửa game/cấu hình/DB/ZIP và không chạy lại test game hoặc demo. Các kết quả kiểm thử trong lời thoại vẫn được ghi rõ là chứng cứ lưu ngày 11/10. Số dòng code cần đối chiếu lại nếu source thay đổi sau ngày soạn.

## Kịch bản thuyết trình theo thành viên — 19/10/2026

- Tạo `docs/presentation/Kich_ban_thuyet_trinh_Poker_Nhom_1_20261019.docx`, 15 trang, lời thoại riêng cho Khải/Hiệp/Nghĩa/Thuận theo phân vai báo cáo; có chuyển người, demo, 15 câu hỏi bảo vệ, bản rút gọn 10 phút và bảng vị trí source. Thời lượng 18–20 phút là gợi ý để tập, chưa đo thực tế.
- Đối chiếu source/pom/schema, audit và XML đã lưu ngày 11/10: 74 unit, 12 integration, năm lượt UI mỗi lượt một scenario. Không chạy lại test game hay demo trong đợt soạn tài liệu.
- Word cập nhật mục lục và kiểm tra cả bảy dẫn trang; render và xem toàn bộ 15 trang. Sửa font/theme và đường kẻ Title sau lần QA đầu. QA cuối tại `target/presentation-script-20261019-qa-v3/`; self-check `docs/presentation/check_script_20261019.py` PASS.
- Source `client.properties` hiện trỏ `127.0.0.1:19090`; kịch bản phân biệt endpoint cục bộ này với endpoint Radmin theo kế hoạch `192.0.2.10:9090`. Không đổi cấu hình, source game, DB, firewall, Radmin hoặc đóng gói lại. Chưa xác minh kết nối máy thứ hai hay môi trường demo ngày báo cáo.

## Đóng gói client mới và cẩm nang hiểu hệ thống — 11/10/2026

- ✅ Build portable bằng PowerShell 7; 74 unit tests chạy lại, 0 failure/error. ZIP `releases/PokerClient-Windows-x64-20261011-185833.zip`, 66.858.678 byte, kèm Java 21.0.10 và guide máy khách. Bản copy ở releases không bị Maven clean xóa; giữ các release cũ.
- ✅ CRC, runtime, client-only allowlist, guide và bytecode/resource hiện tại khớp target/classes; jdeps không missing dependency. SHA256: `B5BE8BA7E7945C0953DC03D32A0F79383F56B6275E103A579449D1D07A8B6BEE`. Không server launcher/persistence/MariaDB driver/SQL/DB credentials trong gói.
- ✅ Launcher EXE thật mở, Responding=True; đóng đúng process smoke, không login hoặc restart server đang có listener 9090. 🟡 Lúc kiểm tra không có IPv4 Radmin 26.* được gán, nên chưa xác nhận auto-connect/PONG qua VPN của release mới hoặc máy thứ hai/Windows sạch không Java.
- ✅ Cẩm nang mới `docs/handbook/Hieu_toan_bo_he_thong_Poker_20261011.docx`: 34 trang, 18 mục, 76 đề mục TOC, 12 sơ đồ, 11 bảng và từ điển. Giải thích nền tảng, source/build/runtime, UI/TCP/thread, luật/pots/wallet, auth/RBAC/reconnect, JDBC/coin/Admin, vận hành, test và sửa hệ thống với AI. Không phải báo cáo học phần; không ghi đè mẫu/báo cáo.
- ✅ Word cập nhật mục lục, đối chiếu cả 76 đề mục với trang thật; Word/LibreOffice QA PDF cùng 34 trang. Render và kiểm tra toàn bộ trang, bảng/sơ đồ, caption/alt text và danh sách Word thật. Nguồn Markdown/PlantUML và đối chiếu source/QA lưu trong docs/handbook; không upload source lên dịch vụ render công khai. Skill tài liệu/sơ đồ giúp giữ semantic headings/TOC, chia ERD thành hai view và sửa activity disconnect theo policy 11/10.
- Đợt này không sửa source game/schema, production DB, firewall hoặc Radmin; không tự cập nhật process server đang chạy. DB/TCP integration và năm DPI trong cẩm nang là chứng cứ review trước cùng ngày, không gọi là chạy lại khi packaging. Server phải chạy source tương ứng và chỉ thay ở ranh giới hand an toàn.

## Review trạng thái bàn, mất kết nối và regression — 11/10/2026

- ✅ Action tray thu nhỏ padding/nút, trạng thái đặt cạnh nhau, controls tiền/cược nằm bên phải và chỉ hiện trong lượt của mình. Không giảm font trạng thái; giữ pot, side pot, nhật ký và Ready/Leave. UI skill được dùng để giữ thông tin đọc được, hạn chế controls không có quyền thao tác và kiểm tra viewport/DPI.
- ✅ Disconnect ngoài hand giải phóng seat ngay; giữa hand giữ cards/contribution và deadline để resume. Sau settlement mới xóa offline seat, không mất payout của offline winner. Ghế đã reconnect được giữ. Heartbeat dọn empty runtime và đối soát disconnect callback bị bỏ lỡ; thao tác disconnect lặp không tăng stateVersion vô ích.
- ✅ Lệnh từ connection bị thay thế bị từ chối; player command, authentication và admin target mutation dùng chung per-user lock. Không giữ đồng thời lock Admin và lock target. Receiver cũ chỉ đọc/đóng socket cũ, không ảnh hưởng socket mới.
- ✅ Codec từ chối frame JSON `null`; Fold khi Leave được ghi vào settlement action log; CLI không in session token/password và không sửa payload gốc để che thông tin.
- ✅ `mvnw.cmd -q -Pdb-it verify`: **74 unit + 12 integration tests PASS**, 0 failure/error/skip, gồm 10 ServerEndToEndIT + 2 JdbcPersistenceIT; build JAR thành công. Chỉ dùng `poker_test`, không migration hay thay dữ liệu `poker_game`.
- ✅ Test có seed 20261011 chạy **60 hand ở bàn 2–8 người**, phối hợp Fold/Check/Call/All-in và stale action; kiểm tra bảo toàn chip, số dư không âm và settlement một lần. Không gọi đây là kiểm thử mọi tổ hợp Poker.
- ✅ **UiSmokeIT PASS tại 100/125/150/175/200%**, mỗi mức một scenario headful với nhiều assertion, 0 failure/error/skip. Kiểm tra status tray, slider/input, controls có quyền, resize/fullscreen/minimize và layout tám ghế/nhiều pot. Lần chạy cuối chờ snapshot/visibility thật, không nhầm disabled do action pending với lượt mới; không kiểm tra bounds của các nút nằm trong parent đã ẩn.
- XML kiểm chứng riêng từng profile: `target/review-qa-20261011/`. Chi tiết test, ảnh fixture và giới hạn: [AUDIT_RESULTS_20261011.md](AUDIT_RESULTS_20261011.md).
- Không restart game server production, không chỉnh firewall/Radmin, không thêm dependency/schema, không sửa báo cáo Word hoặc đóng ZIP. Bản ZIP 08/10 chưa có các sửa đổi source lần này. MariaDB đã được bật theo cấu hình XAMPP hiện có, listener chỉ ở 127.0.0.1:3306.
- 🟡 Chưa nghiệm thu máy thứ hai qua Radmin, mô phỏng DB treo/mất điện hoặc load/soak nhiều bàn dài hạn; không tuyên bố game hết mọi bug.

## Đóng gói client và cập nhật báo cáo — 08/10/2026

- ✅ Đóng gói `target/distribution/20261008-191912/PokerClient-Windows-x64-20261008-191912.zip` (66.858.076 byte), kèm Java 21.0.10 và hướng dẫn máy khách. SHA-256: `30347FD61B086821D61CDC6B29B943115D4003B9286E3EBE4757035F383FBD94`. Giữ các release cũ.
- ✅ Kiểm tra ZIP CRC, allowlist, guide, class bytecode so với target/classes và default endpoint 192.0.2.10:9090. Không chứa server launcher, persistence, JDBC driver hoặc DB credentials. `jdeps --multi-release 21` không có missing dependency. Launcher portable thật auto-connect và trả PONG cho TCP peer trên host; client kiểm thử đã đóng.
- ✅ Chạy lại 64 unit tests, 11 DB/TCP tests (9 ServerEndToEndIT + 2 JdbcPersistenceIT) trên poker_test và 1 UiSmokeIT tại DPI 100%; tất cả 0 failure/error/skip. XML lưu tại target/release-qa-20261008, phân biệt từng profile, không cộng XML cũ còn trong thư mục Failsafe. Năm mức DPI và demo hand thật ngày 05/10 là chứng cứ lịch sử, không gọi là chạy mới.
- ✅ Báo cáo mới `docs/reports/Bao_cao_Lap_trinh_mang_Game_Poker_Nhom_1_20261008.docx`: 60 trang, 9 chương, 17 hình, 30 bảng và 12 nguồn IEEE. Giữ nguyên khung/logo trường và roster; bổ sung DEALING/BETTING, TABLE_ACTION, hiệu ứng/slider, vòng đời cửa sổ, kết quả test và release mới. Word cập nhật TOC/danh mục; render PDF và kiểm tra trực quan toàn bộ 60 trang, không tràn bảng/hình hoặc chồng khối. Không ghi đè báo cáo cũ/mẫu trường.
- Không sửa game/schema production, firewall hoặc cấu hình Radmin; không restart server đang có ván. MariaDB được khởi chạy với cấu hình XAMPP hiện có để test, chỉ bind 127.0.0.1:3306.
- 🟡 Chưa nghiệm thu máy thứ hai qua Radmin, Windows sạch không Java hoặc load/soak dài hạn. Khi triển khai, server và client phải cùng phiên bản; chỉ thay server ở ranh giới hand an toàn.

## Pha chia bài và hiệu ứng quan sát — 05/10/2026

- ✅ Server điều phối DEALING → BETTING trong IN_HAND. Mỗi lá bay 350 ms, cách lượt phát 450 ms, thêm 200 ms dự phòng. Trong DEALING không currentTurn/ACTION_REQUIRED/turn timeout; cược sớm bị NOT_READY. Bắt đầu đủ 20 giây sau chia. All-in runout lần lượt Flop/Turn/River thay vì nhảy thẳng showdown.
- ✅ Scheduler chỉ enqueue callback, kiểm tra generation/hand/running/phase; leave có thể kết thúc hand ngay và vô hiệu callback cũ. Snapshot chứa phase, duration/remaining/cardCount; reconnect dùng tiến độ tương đối và System.nanoTime. Hiện table trước khi tiếp tục animation resume/spectate. Client chậm hội tụ về BETTING, không đợi ACK của client.
- ✅ TABLE_ACTION authoritative, gửi mọi Player/Observer trước chuyển vòng/settlement: sequence, user, street, action, paid, streetContribution, automatic. Gồm blinds, timeout và leave Fold. Snapshot giữ 16 action, UI hiển thị sáu action gần nhất và badge từng ghế. Snapshot resume không replay xu; event trùng/reject không tạo hiệu ứng.
- ✅ Xu Canvas bay từ avatar vào pot 550 ms cho paid > 0 (blinds/Call/Bet/Raise/All-in); Check/Fold không có xu. Slider tối đa 220 logical px, ngay trên ô nhập và vẫn đồng bộ chính xác hai chiều. Không thay logic wallet/betting/pots.
- ✅ Rồng Canvas dài khoảng 45% viewport, bay 5 giây trong cửa sổ game; bảo vệ bảng bài/ghế khỏi overlay, overlay mouseTransparent. Khung/avatar người nhận pot sáng vàng, THẮNG +amount giữ đến hand mới; winnerPayouts hỗ trợ resume sau settlement. Refund vẫn tách tiền thắng; lỗi settlement xóa payout metadata. Reduced/Off giữ thông tin tĩnh; lifecycle cancel dọn xu/bài/rồng; Ready không chờ trang trí.
- ✅ Script portable bổ sung HandPhase/DealTiming/TableAction vào allowlist dùng chung, không đưa server/persistence/JDBC vào client. PowerShell AST parse và kiểm tra class tồn tại PASS; **không chạy script đóng gói**. ZIP cũ SHA-256 vẫn E5D17CF82DFEC06BA1AA71A1A09A04BE424BFFFF2926762A778AA983A663DFC9.
- Skill UI/UX được áp dụng cho motion có thể hủy, thông tin tĩnh không phụ thuộc animation, native slider gọn và lớp trang trí không bắt input. Không thêm dependency, không tải/truyền ảnh, không migration poker_game, không sửa báo cáo Word.

### Kiểm chứng bản này

- `mvnw.cmd -q test`: **64 unit tests PASS**, 0 failures/errors/skips. Có pha thật ở 2/3/8 người, timer 20 giây sau chia, reconnect trong pha, reject không mutation, all-in đi từng street, leave vô hiệu callback và lịch sử 16 action.
- `POKER_DB_TEST_URL=jdbc:mariadb://127.0.0.1:3306/poker_test`, `-Pdb-it test-compile failsafe:integration-test failsafe:verify`: **11 tests PASS** (9 TCP + 2 JDBC), 0 failures/errors/skips. Test cũ gửi Fold trong DEALING từng thất bại; sửa điều kiện chờ BETTING rồi chạy lại đầy đủ. Chỉ bootstrap/migration fixture trong poker_test; không thay dữ liệu poker_game.
- UI smoke cuối ở **100/125/150/175/200%**: mỗi JVM 1 scenario PASS, 0 failures/errors/skips. Kiểm tra các kích thước cửa sổ, phát riêng ba lá, chặn cược trong pha, slider nằm trên input/không giãn, Raise/Check badge, xu đúng amount/de-dup/resume, winner avatar/frame, overlay toàn viewport/mouseTransparent, Full/Reduced/Off, 20 fixture hand hòa/side-pot, các role/navigation/Ready và minimize heartbeat.
- Lần UI 200% đầu thất bại vì export PNG trên FX thread giữa hai assertion timing làm qua slot lá thứ ba; chuyển export ra sau assertion và chạy lại PASS. Không thay tốc độ production để làm test qua.
- Ảnh QA fixture: `target/ui-smoke/paced-final-{100,125,150,175,200}/` (dealing-sequential, raise-and-coins, slider, winner-celebration, payout, table-eight, navigation). Đã xem ảnh xu/thanh cược 150%, winner 200% và kết quả client thật. Các fixture không phải bằng chứng chơi từ máy thứ hai.
- **Demo thật PASS**: ba client JavaFX + server mới + MariaDB, TCP cùng máy qua IP Radmin 192.0.2.10:9091. Bàn tám ghế, ba người ngồi; Raise 300, Fold, Call, Bet bằng slider, Check đủ bốn street; wrong turn/privacy/reconnect cùng ghế; settlement JDBC/ledger đúng. Hand 2e7f6b47-b480-4637-9151-182bb7be110c, pot 1.234; wallets 9.408 / 9.950 / 10.642, tổng 30.000.
- Log demo: `.codex-tmp/paced-clients-20261005-202531-stdout.log`; ảnh `.codex-tmp/radmin-demo-20261005-202551/`. Windows để mở để thử Ready ván tiếp. Demo server PID 25940, client PID 29500; MariaDB đã khởi chạy từ cấu hình XAMPP hiện có, PID 9156, chỉ bind 127.0.0.1:3306. Đây là ghi nhận tại lúc chạy, không phải PID cố định cho lần sau.
- Không ngắt/restart server production, không chỉnh firewall/Radmin. Demo riêng dùng poker_test/9091; không chứng nhận remote VPN hoặc Windows không Java. Bản source server/client phải đồng bộ; ZIP cũ chưa có TABLE_ACTION/snapshot mới. Chưa benchmark GPU/heap dài hạn và không tuyên bố hết mọi bug.

| Milestone | Trạng thái | Kết quả |
|---|---:|---|
| M0 — Repository baseline | ✅ | Java 21, Maven Wrapper, cấu trúc project, README và script chạy |
| M1 — OOP domain | ✅ | Card, enum, deck ngẫu nhiên/cố định; test đủ 52 lá duy nhất |
| M2 — Protocol codec | ✅ | JSON length-prefix, giới hạn 1 MiB, partial/multiple/invalid frame tests |
| M3 — Hand evaluator | ✅ | Xếp hạng 5–7 lá, wheel, kicker, board plays và tie |
| M4 — Betting và pots | ✅ | Bet/raise/all-in, short all-in, side pot, folded contributor và odd chip |
| M5 — Basic server | ✅ | Blocking `ServerSocket`, `ClientHandler`, ping/pong |
| M6 — Multi-client | ✅ | Bounded pool, registry, mỗi bàn một command queue/game thread |
| M7 — Basic client | ✅ | Connect, sender/receiver, dispatcher, state và shutdown |
| M8 — CLI end-to-end | 🟡 | Đã chơi và settlement một hand qua TCP bằng 2 client; còn demo đủ các street |
| M9 — Lobby và table | ✅ | Register/login, create/join/invite/ready/spectate |
| M10 — Full network game | 🟡 | Dealer, blinds, timer, action validation, payout từng pot và next-hand đã có; còn manual scenario all-in/side-pot |
| M11 — JavaFX UI | 🟡 | Auth tự kết nối/reconnect, page Home/Table/History/Wallet/Admin, card face, action-aware controls và payout animation; còn manual table smoke test |
| M12 — JDBC persistence | ✅ | MariaDB repositories, PBKDF2, session, ledger và settlement idempotent |
| M13 — Reconnect | ✅ | TCP E2E mất kết nối giữa hand, resume cùng seat và nhận `STATE_SNAPSHOT` đúng version/role |
| M14 — Observer | 🟡 | TCP E2E Observer thấy mọi hole card và bị chặn action; UI observer còn cần manual smoke test |
| M15 — Hardening | ✅ | Validation, frame limit, heartbeat, timeout và graceful shutdown |
| M16 — Final demo | 🟡 | Script và checklist đã có; chưa chạy demo thủ công 3 player + 1 observer |
| M17 — Account/Admin/Coin | 🟡 | RBAC, coin request transaction, realtime Admin/Player UI, audit và portable client chỉ chứa mã client đã triển khai; còn firewall elevated và acceptance trên máy thứ hai |

## Kết quả kiểm thử đợt audit 30/09/2026

- `mvnw.cmd test`: **PASS — 46 unit tests**, gồm bàn 8 người/từ chối người thứ 9, full streets, short/cumulative all-in, uncalled refunds, dead side pot, bảo toàn chip, timeout/reconnect, queue cancellation, bounded network và retirement bàn trống.
- `mvnw.cmd -Pdb-it verify`: **PASS — 11 integration tests**, không skip, với MariaDB/XAMPP thật: coin/admin transactions, RBAC, login/reset/coin-join race, reconnect, wrong table/hand, 3 Player + Observer all-in, dashboard sau payout và Observer role guard.
- `mvnw.cmd -Pui-it verify`: **PASS — 1 headful UI smoke scenario**, nhiều assertion: FXML thật + TCP peer, login giữ nguyên password, Home/History/Wallet/Admin/Observer routing, mọi action button, Ready/Unready, payout/refund, leave rejection recovery, coin pending, Admin search, 52 card faces và bố cục 8 ghế/nhiều pot. Đây không thay thế test remote qua Radmin.
- Portable app smoke sau audit (30/09): launcher `PokerClient.exe` của ZIP mới tự kết nối `192.0.2.10:9090`, trao đổi frame PING/PONG với TCP peer trên host. JAR không chứa server entrypoint, persistence hoặc MariaDB driver. Peer/process kiểm tra đã đóng; chưa chứng nhận máy Windows thứ hai/no-Java.
- TCP E2E: 2 client register, create/join/ready, payout chính xác 10.050/9.950, lưu settlement, bảo toàn tổng chip và bắt đầu ván kế tiếp.
- Database test dùng schema riêng `poker_test`; dữ liệu test có username ngẫu nhiên để cô lập khỏi `poker_game`.
- [Báo cáo audit và giới hạn](AUDIT_RESULTS_20260930.md), [nguồn luật poker/UI](POKER_RULES_UI_AUDIT_20260930.md), ảnh render trong `screenshots/audit-20260930/`.

## Sửa lỗi trong đợt audit 30/09

- Timeout không còn bị hủy bởi stateVersion phụ hoặc kéo dài khi reconnect; timer dùng identity của turn và deadline cố định.
- ALL_IN không bypass quyền raise; short opening/short BB giữ chuẩn BB; có round-robin deal và burn.
- Tách hoàn cược chưa được call khỏi winnings, tránh pot eligibility rỗng làm showdown kẹt; house rule dead side pot được ghi rõ.
- Login thường khôi phục đúng ghế; wallet/quyền được đọc trong shared user lock; auth/admin không race; logout revoke và rời bàn.
- Action gắn/check `tableId + handId + stateVersion`; observer/player projection không kế thừa bài người cũ tại ghế tái sử dụng.
- Network writer queue có giới hạn; từ chối connection thứ 65; queued command timeout không mutation trễ; bàn trống giải phóng thread.
- UI dùng felt/ivory/charcoal, vector deck đủ 52 lá, focus rõ, không che pot bằng seat; network write ngoài FX thread, pending controls và leave error recovery.
- Không cho đổi account role khi vẫn đang spectate; Admin nhận dashboard wallet cập nhật sau hand settlement.

## Giới hạn bàn 8 người

Giới hạn bàn cập nhật 30/09: chọn 2–8 ghế, mặc định 8; server/lobby/runtime và UI cùng chặn trên 8. Observer không chiếm ghế. Test bàn đủ 8 người hoàn tất một hand, bảo toàn 80.000 chips và từ chối người thứ 9. Không sửa dữ liệu/history cũ trong DB. Cần restart server và thay client portable để áp dụng.

## Việc còn lại để nghiệm thu đầy đủ

- Smoke thủ công JavaFX login/lobby/table/observer trên 2+ máy (launcher và auto TCP connect đã smoke-test).
- Chạy demo 3 player + 1 observer gồm showdown, invalid action, all-in/side-pot và disconnect/resume.

## Cửa sổ và DPI — 04/10/2026

- Bỏ minimum 1100×800 và chế độ luôn mở maximized. Client khởi động ở cửa sổ vừa, giới hạn preset theo vùng làm việc màn hình hiện tại; người dùng có thể kéo cạnh/góc (minimum nhỏ 360×280).
- Menu **Hiển thị** trên mọi trang: 960×640, 1280×800, 1600×900, phóng to, toàn màn hình; F11 bật/tắt, Esc thoát toàn màn hình.
- `ClientWindow` dùng viewport JavaFX chung: layout mở rộng khi đủ diện tích và scale đồng đều khi thiếu diện tích. Giữ theme, tỷ lệ lá bài, vị trí tám ghế, pot và controls; không thay đổi state/socket khi resize hoặc đổi chế độ. Navbar dùng FlowPane để không ép các nút ra ngoài.
- Test 100% phát hiện mép trang History vượt 0,53 logical pixel do pixel snapping sau scale lẻ. Đã tắt snapping ở shell/page host, hoàn tất layout content trong lượt resize và giữ assertion chống clipping.
- UI smoke cuối **PASS ở output scale 100/125/150/175/200%**, mỗi process có assertion xác nhận JavaFX thực sự áp dụng scale yêu cầu. Đây là override của JavaFX Glass trên Windows, không thay đổi Display Settings của máy.
- Mỗi scale kiểm tra tám kích thước yêu cầu: 360×280, 640×360, 800×450, 960×640, 800×1000, 1600×500, 1280×800, 1600×900; native window manager có thể giới hạn kích thước thực tế. Assertions dùng scene thực tế để kiểm tra Login/Lobby/History/Wallet/Table/Observer/Admin, bounds của các nút/bài chung/ghế/pot, seat overlap, preset nằm trong vùng làm việc và handler fullscreen/maximize.
- `mvnw.cmd -q test` chạy lại sau bản sửa cuối: **PASS — 46 unit tests, 0 failure/error**. Không chạy lại DB integration trong đợt này vì không thay đổi backend/persistence.
- Giữ regression TCP fixture: đăng nhập, mọi action, Ready/Unready, payout/refund, leave rejection, coin request, Admin search và Observer controls. Không coi fixture là demo Radmin từ máy thứ hai.
- Chứng cứ từng scale: `target/ui-smoke/dpi-100/`, `dpi-125/`, `dpi-150/`, `dpi-175/`, `dpi-200/`, gồm ảnh và `test-results.txt` (mỗi lần 1 scenario, 0 failure/error/skip). Các ca UI chạy bằng `-Pui-it test-compile failsafe:integration-test failsafe:verify` với `-DargLine=-Dglass.win.uiScale=...` để không cần đóng gói.
- API được đối chiếu với [JavaFX 21 Stage](https://openjfx.io/javadoc/21/javafx.graphics/javafx/stage/Stage.html), [Screen](https://openjfx.io/javadoc/21/javafx.graphics/javafx/stage/Screen.html) và [nguồn Glass Windows](https://github.com/openjdk/jfx/blob/jfx21/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinApplication.java). Override DPI chỉ dùng trong kiểm thử, không đưa vào launcher.
- Chỉ thay đổi client UI/test/README/nhật ký; không sửa server/game engine/protocol/schema, không migration hay đóng gói portable. Chạy bản mới bằng `run-ui.cmd`; ZIP portable cũ chưa được cập nhật. Cửa sổ quá nhỏ vẫn hiển thị đầy đủ nhưng chữ/nút sẽ nhỏ, nên chọn preset lớn hoặc phóng to để đọc rõ.

### Nút trên thanh tiêu đề — 04/10/2026

- Khai báo tường minh `StageStyle.DECORATED` trong `ClientWindow.configure`, giữ `resizable=true` để sử dụng nút thu nhỏ/phóng to–khôi phục/đóng chuẩn Windows. Cấu hình dùng chung cho Player, Observer và Admin; không dựng caption bar khác hoặc bổ sung GUI cho backend console.
- UI smoke ở output scale 150% **PASS — 1 scenario, 0 failure/error/skip**, gồm các assertions trước đó và kiểm tra style, minimize/restore, trạng thái đăng nhập còn nguyên, PING nhận PONG trong lúc cửa sổ thu nhỏ. Test đặt window state qua JavaFX API, không giả thành kiểm thử click từng nút native.
- Chứng cứ: `target/ui-smoke/titlebar-150/test-results.txt` và ảnh fixture cùng thư mục. Không đóng gói portable lại; nhấn Esc/F11 để hiện thanh tiêu đề khi đang toàn màn hình.
- Tham chiếu API: [JavaFX 21 StageStyle](https://openjfx.io/javadoc/21/javafx.graphics/javafx/stage/StageStyle.html).

## Đóng gói client và cập nhật hướng dẫn — 05/10/2026

- Theo yêu cầu đóng gói: tạo `target/distribution/20261005-130213/PokerClient-Windows-x64-20261005-130213.zip`, 66.832.600 bytes. SHA-256: `E5D17CF82DFEC06BA1AA71A1A09A04BE424BFFFF2926762A778AA983A663DFC9`.
- Dùng JDK 21.0.10 nhất quán cho Maven/JAR/jpackage; PATH có jar/jpackage của JDK 25 nên script nay lấy tools theo java.home. Mỗi build có input/staging/output mới, không xóa distribution cũ hoặc chạy Maven clean làm mất chứng cứ QA.
- Build `package` PASS, gồm 46 unit tests (0 failures/errors/skips). UI smoke chạy lại ở 150%: 1 scenario PASS, 19,32 giây; ảnh tại `target/ui-smoke/release-20261005/`. Không chạy DB integration trong đợt release chỉ đổi UI/đóng gói này.
- Launcher EXE thực tế PASS: tự kết nối IP Radmin 192.0.2.10:9090 và trả PONG cho TCP fixture cùng máy. Lần thử đầu peer hết timeout vì launcher được mở sau khi xem ảnh; chạy lại đúng thứ tự đã PASS. Peer và process thử đã dừng, không ghi dữ liệu DB hoặc dùng credentials.
- ZIP CRC PASS; đối chiếu từng class với target/classes, runtime Java 21.0.10, host mặc định và DOCX kèm theo. Chỉ có hai snapshot DTO trong package server; không có server entrypoint/persistence/JDBC, SQL hoặc script máy chủ. Check tái chạy: `.codex-tmp/verify_portable_release.py <zip>` bằng bundled Python.
- Hướng dẫn mới riêng ngày 05/10: máy chủ 3 trang, máy khách 2 trang, giữ tài liệu cũ. Đã render và xem đủ 5 trang bằng skill documents; QA PDF/PNG ở `.codex-tmp/release-guides-20261005/*-final/`. Hướng dẫn máy khách nằm trong ZIP.
- Trạng thái máy ở kiểm tra cuối: Radmin adapter Up, IPv4 192.0.2.10; firewall rule Poker Server - Radmin VPN TCP 9090 Enabled=True. Không thấy listener 9090/3306 sau smoke. Chưa khởi chạy server game/MariaDB thay người dùng; chưa nghiệm thu máy thứ hai/no-Java. Không thay đổi firewall/Radmin/schema trong đợt này.

## Triển khai ba plan gameplay/UI — 05/10/2026

- ✅ Lượt cơ bản 2/3/8 người vốn đúng, được giữ và bảo vệ bằng assertions từng street. Test red tái hiện riêng lỗi 3→2 người: previous BB bị thu BB lại; sửa ở startHand và lưu D/SB/BB vào snapshot, không xoay trong hand. Có regression ghế trống, Fold SB/D, all-in SB, raise/re-raise, timeout/disconnect; hand nhiều người còn hai do Fold không đổi thành heads-up giữa hand.
- ✅ Slider 1.001 vị trí + ô nhập số nguyên đồng bộ hai chiều; tính bằng BigInteger chống overflow, ô nhập giữ độ chính xác đến Long.MAX_VALUE. Giữ text đang nhập/sai, hint inline, prompt mới reset mức/giới hạn, short all-in khóa tại max, Observer không có controls. Kéo thanh không gửi action; Bet/Raise mới gửi TCP.
- ✅ Test red phát hiện Bet/Raise quá stack bị clamp thành all-in. Thêm validation chung trong BettingRound trước mutation, giữ Call/all-in hợp lệ.
- ✅ Chia riêng từng lá, hai pass clockwise sau D, bài chung mới được animate; giữ node ghế/bài qua snapshot. Deck Canvas gần D, nghiêng/bóng nhẹ. Prompt của chính mình kết thúc ngay animation để giữ deadline server. Không download card pack/ảnh hoặc thêm Three.js/dependency.
- ✅ Chúc mừng tên người thắng/bản thân, chip từng pot, refund tách riêng; rồng vàng và lửa quanh mọi ghế nhận pot khoảng 2,5 giây. Hòa/side pot có nhiều winner; de-dupe tableId/handId, không chúc mừng khi settlement thất bại. Ready không bị animation khóa.
- ✅ TableEffects tách trang trí khỏi network/state. Menu Full/Reduced/Off lưu Preferences; Reduced/Off giữ kết quả/tier, bỏ chuyển động. Resize tính lại đường bay; leave/logout/disconnect/new hand hủy node tạm. Minimize dừng trang trí, không ảnh hưởng heartbeat; restore không replay trang trí cũ.
- ✅ GlorySummary do server tạo, hai bậc độc lập có thể giảm: Wealth theo settled Wallet, Achievement theo gamesPlayed và tỷ lệ nhận pot. Giữ định nghĩa wins hiện tại (cả hòa, không nhất thiết lãi ròng). Viền ghế/badge, tooltip hồ sơ/ranking có thống kê và điều kiện bậc tiếp. Null stats hiển thị chưa có thống kê. Per-seat public cache seed từ DB khi join, chỉ tăng sau commit; không thêm query trên mỗi action/snapshot. Không migration.
- ✅ WALLET_UPDATED sau commit gửi cả người đã rời bàn, kèm glory; lỗi refresh/notification không rollback tiền đã commit. DB E2E đối chiếu glory với persisted wins/games/chips. Coin approval không tăng counters. Test duplicate settlement và rollback giữa transaction giữ balance/counters/history đúng.
- Skill UI/UX dẫn tới native slider, giữ form đang nhập, reduced motion và overlay mouseTransparent; domain-modeling tách WealthTier/AchievementTier khỏi Wallet/TableStack/AccountRole. Chi tiết và nguồn luật chính thức tại `docs/GAMEPLAY_UX.md`.

### Kiểm chứng cuối

- `mvnw.cmd -q test`: **58 unit tests, 0 failures/errors/skips** (PokerTableRuntime 32, Betting/Pot 8; còn lại codec/domain/lobby/transport/password/range/glory).
- `POKER_DB_TEST_URL=jdbc:mariadb://127.0.0.1:3306/poker_test` và `-Pdb-it test-compile failsafe:integration-test failsafe:verify`: **11 tests PASS** (9 TCP E2E + 2 JDBC), 0 failures/errors/skips. Schema/test fixtures chỉ trong poker_test; không migration hoặc sửa dữ liệu poker_game.
- `-Pui-it test-compile failsafe:integration-test failsafe:verify`, output scale **100/125/150/175/200%**: mỗi process 1 scenario PASS, 0 failures/errors/skips. Tám kích thước cửa sổ, Slider/input/short all-in, controls mọi action, early prompt, giữ card node, duplicate result, 20 fixture hands hòa/side-pot/node cleanup, Observer/Admin/coin/Ready/navigation và minimize heartbeat.
- Ảnh/biên bản: `target/ui-smoke/features-final-1.0/`, `features-final-1.25/`, `features-final-150/`, `features-final-1.75/`, `features-final-2.0/`; mỗi thư mục có test-results.txt. Đã xem ảnh slider, winner-celebration và tám ghế. Đây là TCP UI fixture, không nghiệm thu máy thứ hai; kiểm tra node count không phải heap/performance profiling.
- UI smoke đã bắt lỗi dấu chọn RadioMenuItem không cập nhật qua action; sửa handler và chạy lại. Không che giấu lần red trước fix.
- Không chạy jpackage hoặc tạo ZIP. SHA-256 của portable cũ vẫn `E5D17CF82DFEC06BA1AA71A1A09A04BE424BFFFF2926762A778AA983A663DFC9` (66.832.600 bytes).
- Snapshot mới thêm blind seats/glory: phải dùng server và client cùng bản. Server java PID 28096 đang giữ port 9090, khởi chạy từ 13:24, trước bản sửa; MariaDB PID 23120 chỉ nghe 127.0.0.1:3306. Không ngắt tiến trình/đổi firewall/Radmin; muốn thử source mới phải restart server lúc không có hand đang chạy và chạy client bằng run-ui.cmd. ZIP cũ chưa có thay đổi này.
- Chưa có demo từ máy thứ hai, chưa benchmark GPU/heap dài hạn; không tuyên bố hệ thống không còn mọi bug. Academic DOCX và hướng dẫn Word cũ không bị sửa.
> Địa chỉ Radmin của máy thử nghiệm được thay bằng IP minh họa `192.0.2.10` khi công bố nhật ký.

