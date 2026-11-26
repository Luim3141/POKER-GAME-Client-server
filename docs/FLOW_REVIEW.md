# Review luồng hoạt động

Cập nhật: **2026-10-11**. Luật và UI contract tham chiếu [POKER_UX_RESEARCH.md](POKER_UX_RESEARCH.md).

## Mất kết nối và quyền sở hữu ghế

- WAITING/BETWEEN_HANDS: disconnect giải phóng ghế ngay, không giữ Ready hoặc reservation cho lần đăng nhập sau.
- IN_HAND: giữ seat/cards/contribution để resume; không reset deadline. Timer vẫn tự Check nếu không cần theo, hoặc Fold nếu đang facing bet. All-in offline vẫn được xét thắng pot.
- Settlement hoàn tất hoặc thất bại được xử lý trước khi xóa offline/deferred-leave seats. Người thắng offline nhận wallet đúng; nếu quay lại sau hand đã kết thúc thì vào lobby, không phục hồi ghế cũ.
- Connection mới thay connection cũ trong registry; lệnh cũ và disconnect callback cũ không được sửa seat của connection mới. Player commands và authentication dùng cùng per-user lock.
- Heartbeat đối soát callback disconnect bị bỏ lỡ và dọn runtime rỗng; còn online Observer thì giữ bàn. Không dựa vào việc người dùng bấm tải lobby để dọn bàn.
- Hành động Fold khi Leave có cả TABLE_ACTION công khai và bản ghi lịch sử trong settlement.

Kiểm chứng ngày 11/10: [AUDIT_RESULTS_20261011.md](AUDIT_RESULTS_20261011.md). Các bảng bên dưới ghi nhận các sửa đổi những đợt trước.

## Luồng hiện tại

```text
Auth → Home/Lobby → Player Table | Observer Table → Home
                  ↘ History → Home
Disconnect → Auth/Reconnect → Resume session → Home hoặc Table snapshot
```

- `Auth`: app tự kết nối nền khi mở và thử lại nếu server chưa sẵn sàng; Login/Register cũng tự kết nối nếu cần. Sau mất mạng, client tự resume session trên socket mới.
- `Home`: create/join/watch/invite/history; user chỉ được giữ một player seat.
- `Table`: Waiting → Ready/Unready → Pre-flop → Flop → Turn → River → Showdown/Result → Ready lại.
- `Result`: server chốt settlement → phát `HAND_RESULT` có payout theo từng pot → phát snapshot stack cuối → UI chạy hiệu ứng pot về seat thắng → mở nút `Chơi ván tiếp theo`.
- `Back`: Observer gửi `STOP_SPECTATE`; Player gửi đúng một `LEAVE_TABLE`; rời giữa hand có xác nhận và Fold.
- `History/Wallet`: có thể mở khi đang ngồi bàn; Quay lại trở về bàn, còn Trang chủ xác nhận rời bàn. Snapshot mới không tự đổi page đang xem.
- `Action`: server gửi snapshot có `stateVersion` trước `ACTION_REQUIRED`; UI khóa double-submit và dùng `Raise to`.

## Xung đột đã sửa

| Vấn đề | Cách sửa | Regression check |
|---|---|---|
| Bàn 3+ người start khi mới 2 người Ready | Chỉ start khi mọi player hợp lệ đều Ready | `waitsUntilEveryEligiblePlayerIsReady` |
| `ACTION_REQUIRED` tới trước state mới | Gửi `TABLE_STATE` trước action prompt cùng `stateVersion` | `sendsCurrentSnapshotBeforeRequestingAnAction` |
| Người không tới lượt rời bàn làm nhảy turn | Giữ nguyên current player; chỉ advance khi cần | `nonCurrentPlayerLeavingDoesNotStealTheTurn` |
| Nhiều short all-in cộng dồn không reopen raise | Theo dõi mức bet tại lần action cuối và full-raise increment | `cumulativeShortAllInsReopenRaising` |
| Player vừa spectate vừa join bị giữ role Observer | Xóa observer registration sau join/create thành công | Flow guard trong `LobbyManager` |
| Spectate table sai làm kẹt observer mapping | Resolve table trước khi mutate mapping | Flow guard trong `LobbyManager` |
| Re-auth trên cùng socket tạo ghost connection | Từ chối Login/Register/Resume nếu đã authenticated | Real TCP E2E |
| History chứa `Instant` không serialize được | Map timestamp thành ISO-8601 text tại protocol edge | Real TCP E2E |
| Leave giữa hand cho phép join bàn khác quá sớm | Giữ player-table ownership tới khi hand cũ kết thúc | `LobbyManager` + deferred seat removal |
| Người thắng không thấy chip được cộng | Snapshot giữa ván cũ lấy nhầm `PlayerHandState.stack`; sau settlement phải lấy `TableSeat.stack` | `creditsWinnerAndCanStartTheNextHand` + TCP/MariaDB E2E |
| Kết quả chỉ có tổng theo người, không biết thắng pot nào | `HAND_RESULT` phát main/side pot, eligibility và payout từng winner | Runtime unit test |
| Sau kết quả thiếu bước tiếp tục | Khóa Ready trong lúc payout animation; sau đó hiện `Chơi ván tiếp theo`, đếm đúng player connected còn chip | Runtime + TCP next-hand E2E |
| Blind chênh lệch bị hiển thị như side pot | Khi chưa có all-in, snapshot gộp toàn bộ contribution vào một main pot; chỉ tách layer khi có all-in | `creditsWinnerAndCanStartTheNextHand` |
| Odd chip của pot hòa trao sai cho dealer | Thứ tự payout bắt đầu từ winner đầu tiên bên trái button, dealer đứng cuối vòng | `awardsOddChipToFirstWinnerLeftOfDealer` |
| DB settlement lỗi nhưng client vẫn thấy payout giả | Khôi phục stack và chỉ phát lỗi/final snapshot; không phát `HAND_RESULT` nếu transaction thất bại | `restoresStacksAndDoesNotAnnouncePayoutWhenSettlementFails` |
| Mở app hoặc mất mạng phải bấm Nối thủ công | Kết nối trên worker khi mở app, thử lại định kỳ và resume session | Portable app smoke test + TCP `Established` tới Radmin IP |
| Snapshot bật action trước `ACTION_REQUIRED` | Chỉ bật nút cược khi prompt khớp `stateVersion`, table và user | UI guard + `sendsCurrentSnapshotBeforeRequestingAnAction` |
| Ready trong lúc ván đang chạy | Server từ chối thay đổi Ready cho đến giữa hai ván | Runtime regression |
| Xem Ví/Lịch sử bị snapshot kéo về bàn hoặc nút Back làm Fold | Giữ page đang xem, Back trở về bàn; Home mới rời bàn có xác nhận | UI route guard |
| Rời bàn 3+ người trong hand rồi join bàn khác quá sớm | Lobby trả `seatReleasePending`, UI khóa create/join tới khi hand cũ chốt | Lobby state |
| Observer gửi action nhận lỗi chung | Trả `OBSERVER_CANNOT_ACT` qua `ACTION_REJECTED` đúng protocol | TCP E2E reconnect/observer |
| All-in tạo side pot nhưng luồng tới showdown chưa được kiểm chứng | Chạy 3 người với short stack all-in qua Flop/Turn/River và xác nhận tổng pot/payout | `shortAllInCreatesMainAndSidePotThroughShowdown` |

## UI contract

- Card face dùng chung cho đủ 52 tổ hợp rank/suit; card back riêng; rank không phụ thuộc màu để đọc được.
- Board luôn giữ 5 slot; main pot và từng side pot hiển thị độc lập.
- Seat hiện stack, street bet, Ready/Fold/All-in/Disconnected, dealer và turn highlight.
- Action tray chỉ bật đúng lượt, hiện `To call`, `Min/Max raise to`, stack; amount được kiểm tra trước khi gửi.
- Observer dùng projection theo yêu cầu đồ án (thấy toàn bộ hole cards) nhưng không có Ready/action controls.
- Winner payout và showdown cards được render trước khi ván sau xóa state; mỗi pot bay về seat thắng và stack cuối lấy từ server.
- Timer khi quay lại bàn trừ thời gian đã trôi qua kể từ snapshot hoặc dùng deadline của `ACTION_REQUIRED`.
